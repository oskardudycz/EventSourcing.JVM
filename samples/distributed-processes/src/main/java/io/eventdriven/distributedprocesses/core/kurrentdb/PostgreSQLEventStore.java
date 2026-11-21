package io.eventdriven.distributedprocesses.core.kurrentdb;

import com.zaxxer.hikari.HikariDataSource;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.messaging.MessageValues;
import io.eventdriven.distributedprocesses.core.projections.ProcessorCheckpoints;
import io.eventdriven.distributedprocesses.core.serialization.EventSerializer;
import io.eventdriven.distributedprocesses.core.serialization.EventTypeMapper;
import io.kurrent.dbclient.Position;
import io.kurrent.dbclient.StreamState;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.BackOff;
import tools.jackson.core.type.TypeReference;

public class PostgreSQLEventStore implements EventStore, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLEventStore.class);

    static final String COLUMNS =
            "stream_id, stream_position, global_position, transaction_id, id, type, data::text, metadata::text";

    private static final String CREATE_SCHEMA = """
            create table if not exists streams(
                id               text    not null primary key,
                stream_position  bigint  not null
            );

            create sequence if not exists global_event_position;

            create table if not exists events(
                stream_id        text         not null references streams(id),
                stream_position  bigint       not null,
                global_position  bigint       not null default nextval('global_event_position'),
                transaction_id   bigint       not null default (pg_current_xact_id()::text::bigint),
                id               uuid         not null,
                type             text         not null,
                data             jsonb        not null,
                metadata         jsonb        not null default '{}',
                created          timestamptz  not null default now(),
                primary key (stream_id, stream_position)
            );

            create index if not exists events_in_commit_order on events(transaction_id, global_position);
            """;

    @Override
    public ReadResult read(String streamId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "select %s from events where stream_id = ? order by stream_position".formatted(COLUMNS))) {
            statement.setString(1, streamId);

            var events = new ArrayList<EventEnvelope<Object>>();
            try (var rows = statement.executeQuery()) {
                while (rows.next())
                    storedEventOf(rows).map(StoredEvent::envelope).ifPresent(events::add);
            }

            return events.isEmpty() ? new ReadResult.StreamDoesNotExist() : new ReadResult.Success(events);
        } catch (SQLException e) {
            return new ReadResult.UnexpectedFailure(e);
        }
    }

    @Override
    public AppendResult append(String streamId, Object... events) {
        return append(streamId, StreamState.noStream(), events);
    }

    @Override
    public AppendResult append(String streamId, StreamState expectedRevision, Object... events) {
        var values = context.inherit();

        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);

            try {
                var result = appendIn(connection, streamId, expectedRevision, events, values);

                if (result.succeeded()) connection.commit();
                else connection.rollback();

                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            return new AppendResult.UnexpectedFailure(e);
        }
    }

    @Override
    public MessageContext.Scope continueTraceOf(String stepName, EventMetadata lastEvent) {
        return context.continuing(stepName, lastEvent);
    }

    @Override
    public EventStore subscribe(String name, List<Class<?>> types, Consumer<EventEnvelope<Object>> handler) {
        if (types.isEmpty())
            throw new IllegalArgumentException("Subscription '%s' needs at least one event type".formatted(name));

        if (!subscriptionNames.add(name))
            throw new IllegalArgumentException("Subscription name '%s' is already in use".formatted(name));

        subscriptions.add(new PostgreSQLSubscription(dataSource, name, types, handler, context, checkpoints, backOff));

        return this;
    }

    synchronized void poll() {
        subscriptions.forEach(PostgreSQLSubscription::poll);
    }

    @Override
    public void close() {
        subscriptions.forEach(PostgreSQLSubscription::close);
        poller.shutdownNow();
        try {
            poller.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (ownedDataSource != null) ownedDataSource.close();
    }

    private AppendResult appendIn(
            Connection connection, String streamId, StreamState expectedRevision, Object[] events, MessageValues values)
            throws SQLException {
        var actualRevision = lockRevisionOf(connection, streamId);
        var actual = actualRevision < 0 ? StreamState.noStream() : StreamState.streamRevision(actualRevision);

        if (!matches(expectedRevision, actual))
            return expectedRevision.equals(StreamState.noStream())
                    ? new AppendResult.StreamAlreadyExists(actual)
                    : new AppendResult.Conflict(expectedRevision, actual);

        var lastRevision = actualRevision;
        var lastLogPosition = 0L;

        try (var insert = connection.prepareStatement("""
                insert into events(stream_id, stream_position, id, type, data, metadata)
                values (?, ?, ?, ?, ?::jsonb, ?::jsonb)
                returning global_position
                """)) {
            for (var event : events) {
                insert.setString(1, streamId);
                insert.setLong(2, ++lastRevision);
                insert.setObject(3, UUID.randomUUID());
                insert.setString(4, EventTypeMapper.toName(event.getClass()));
                insert.setString(5, EventSerializer.mapper.writeValueAsString(event));
                insert.setString(6, EventSerializer.mapper.writeValueAsString(metadataOf(values)));

                try (var inserted = insert.executeQuery()) {
                    inserted.next();
                    lastLogPosition = inserted.getLong(1);
                }
            }
        }

        try (var update = connection.prepareStatement("update streams set stream_position = ? where id = ?")) {
            update.setLong(1, lastRevision);
            update.setString(2, streamId);
            update.executeUpdate();
        }

        return new AppendResult.Success(
                StreamState.streamRevision(lastRevision), new Position(lastLogPosition, lastLogPosition));
    }

    private static long lockRevisionOf(Connection connection, String streamId) throws SQLException {
        try (var create = connection.prepareStatement(
                        "insert into streams(id, stream_position) values (?, -1) on conflict (id) do nothing");
                var lock = connection.prepareStatement("select stream_position from streams where id = ? for update")) {
            create.setString(1, streamId);
            create.executeUpdate();

            lock.setString(1, streamId);
            try (var rows = lock.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static boolean matches(StreamState expected, StreamState actual) {
        if (expected.equals(StreamState.any()) || expected.equals(actual)) return true;

        return expected.equals(StreamState.streamExists()) && !actual.equals(StreamState.noStream());
    }

    private static Map<String, String> metadataOf(MessageValues values) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put(MessageContext.CORRELATION_ID, values.correlationId());
        metadata.put(MessageContext.CAUSATION_ID, values.causationId());
        if (values.traceId() != null) metadata.put(TRACE_ID, values.traceId());
        if (values.spanId() != null) metadata.put(SPAN_ID, values.spanId());

        return metadata;
    }

    static Optional<StoredEvent> storedEventOf(ResultSet row) throws SQLException {
        var eventClass = EventTypeMapper.toClass(row.getString("type"));

        if (eventClass.isEmpty()) return Optional.empty();

        var messageId = row.getString("id");
        var metadata = EventSerializer.mapper.readValue(row.getString("metadata"), METADATA_TYPE);
        var correlationId = metadata.getOrDefault(MessageContext.CORRELATION_ID, messageId);

        Object event = EventSerializer.mapper.readValue(row.getString("data"), eventClass.get());

        return Optional.of(new StoredEvent(
                row.getLong("transaction_id"),
                new EventEnvelope<>(
                        event,
                        new EventMetadata(
                                row.getString("stream_id"),
                                row.getLong("stream_position"),
                                row.getLong("global_position"),
                                messageId,
                                correlationId,
                                metadata.getOrDefault(MessageContext.CAUSATION_ID, correlationId),
                                metadata.get(TRACE_ID),
                                metadata.get(SPAN_ID)))));
    }

    record StoredEvent(long transactionId, EventEnvelope<Object> envelope) {}

    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";
    private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;
    private final HikariDataSource ownedDataSource;
    private final MessageContext context;
    private final ProcessorCheckpoints checkpoints;
    private final BackOff backOff;
    private final Set<String> subscriptionNames = ConcurrentHashMap.newKeySet();
    private final List<PostgreSQLSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "postgresql-event-store-poller");
        thread.setDaemon(true);
        return thread;
    });

    public PostgreSQLEventStore(
            DataSource dataSource,
            MessageContext context,
            ProcessorCheckpoints checkpoints,
            BackOff backOff,
            Duration pollInterval) {
        this(dataSource, null, context, checkpoints, backOff, pollInterval);
    }

    public static PostgreSQLEventStore connectingTo(
            String url,
            String username,
            String password,
            MessageContext context,
            ProcessorCheckpoints checkpoints,
            BackOff backOff,
            Duration pollInterval) {
        var dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        return new PostgreSQLEventStore(dataSource, dataSource, context, checkpoints, backOff, pollInterval);
    }

    private PostgreSQLEventStore(
            DataSource dataSource,
            HikariDataSource ownedDataSource,
            MessageContext context,
            ProcessorCheckpoints checkpoints,
            BackOff backOff,
            Duration pollInterval) {
        this.dataSource = dataSource;
        this.ownedDataSource = ownedDataSource;
        this.context = context;
        this.checkpoints = checkpoints;
        this.backOff = backOff;

        createSchema();
        poller.scheduleWithFixedDelay(
                this::pollQuietly, pollInterval.toMillis(), pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void createSchema() {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute(CREATE_SCHEMA);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create the event store schema", e);
        }
    }

    private void pollQuietly() {
        try {
            poll();
        } catch (RuntimeException e) {
            logger.error("Polling the event store failed", e);
        }
    }
}
