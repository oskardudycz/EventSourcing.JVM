package io.eventdriven.distributedprocesses.core.eventstore.postgresql;

import com.zaxxer.hikari.HikariDataSource;
import io.eventdriven.distributedprocesses.core.eventstore.Checkpoint;
import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.messaging.MessageValues;
import io.eventdriven.distributedprocesses.core.messaging.UnreadableMessageException;
import io.eventdriven.distributedprocesses.core.serialization.EventSerializer;
import io.eventdriven.distributedprocesses.core.serialization.EventTypeMapper;
import io.eventdriven.distributedprocesses.core.subscriptions.SubscriptionCheckpoints;
import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.BackOff;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

public class PostgreSQLEventStore implements EventStore, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLEventStore.class);

    static final String COLUMNS =
            "stream_id, stream_position, global_position, transaction_id::text::bigint as transaction_id,"
                    + " message_id, message_type, message_data::text, message_metadata::text";

    private static final String CREATE_SCHEMA = """
            create table if not exists streams(
                stream_id        text    not null primary key,
                stream_position  bigint  not null,
                stream_type      text    not null
            );

            create sequence if not exists global_message_position;

            create table if not exists messages(
                stream_id               text         not null,
                stream_position         bigint       not null,
                global_position         bigint       not null default nextval('global_message_position'),
                transaction_id          xid8         not null,
                message_id              uuid         not null,
                message_kind            char(1)      not null default 'E',
                message_type            text         not null,
                message_schema_version  int          not null default 1,
                message_data            jsonb        not null,
                message_metadata        jsonb        not null default '{}',
                created                 timestamptz  not null default now(),
                primary key (stream_id, stream_position)
            );

            create index if not exists messages_in_commit_order on messages(transaction_id, global_position);

            create or replace function append_to_stream(
                v_message_ids text[],
                v_messages_data text[],
                v_messages_metadata text[],
                v_message_schema_versions int[],
                v_message_types text[],
                v_message_kinds text[],
                v_stream_id text,
                v_stream_type text,
                v_expected_kind text,
                v_expected_position bigint
            ) returns table (
                success boolean,
                stream_position bigint,
                global_positions bigint[],
                transaction_id xid8
            ) language plpgsql as $$
            declare
                v_count int := array_length(v_messages_data, 1);
                v_transaction_id xid8 := pg_current_xact_id();
                v_stream_position bigint;
                v_global_positions bigint[];
            begin
                if v_expected_kind = 'no_stream' then
                    begin
                        insert into streams(stream_id, stream_position, stream_type)
                        values (v_stream_id, v_count - 1, v_stream_type);

                        v_stream_position := v_count - 1;
                    exception when unique_violation then
                        v_stream_position := null;
                    end;
                elsif v_expected_kind = 'at' then
                    update streams as s
                    set stream_position = s.stream_position + v_count
                    where s.stream_id = v_stream_id and s.stream_position = v_expected_position
                    returning s.stream_position into v_stream_position;
                elsif v_expected_kind = 'stream_exists' then
                    update streams as s
                    set stream_position = s.stream_position + v_count
                    where s.stream_id = v_stream_id
                    returning s.stream_position into v_stream_position;
                else
                    insert into streams as s(stream_id, stream_position, stream_type)
                    values (v_stream_id, v_count - 1, v_stream_type)
                    on conflict (stream_id) do update set stream_position = s.stream_position + v_count
                    returning s.stream_position into v_stream_position;
                end if;

                if v_stream_position is null then
                    return query
                        select false,
                               coalesce((select s.stream_position from streams as s where s.stream_id = v_stream_id), -1),
                               null::bigint[],
                               null::xid8;
                    return;
                end if;

                with inserted as (
                    insert into messages(
                        stream_id, stream_position, transaction_id, message_id, message_kind,
                        message_type, message_schema_version, message_data, message_metadata)
                    select v_stream_id,
                           v_stream_position - v_count + m.ordinal,
                           v_transaction_id,
                           m.message_id::uuid,
                           m.message_kind,
                           m.message_type,
                           m.message_schema_version,
                           m.message_data::jsonb,
                           m.message_metadata::jsonb
                    from unnest(
                            v_message_ids, v_messages_data, v_messages_metadata,
                            v_message_schema_versions, v_message_types, v_message_kinds)
                         with ordinality as m(
                            message_id, message_data, message_metadata,
                            message_schema_version, message_type, message_kind, ordinal)
                    order by m.ordinal
                    returning global_position
                )
                select array_agg(i.global_position order by i.global_position)
                into v_global_positions
                from inserted as i;

                return query select true, v_stream_position, v_global_positions, v_transaction_id;
            end;
            $$;
            """;

    private static final String APPEND = """
            select success, stream_position, global_positions, transaction_id::text::bigint as transaction_id
            from append_to_stream(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    @Override
    public ReadResult read(String streamId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "select %s from messages where stream_id = ? order by stream_position".formatted(COLUMNS))) {
            statement.setString(1, streamId);

            var events = new ArrayList<EventEnvelope<Object>>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) events.add(eventOf(rows));
            }

            return events.isEmpty() ? new ReadResult.StreamDoesNotExist() : new ReadResult.Success(events);
        } catch (SQLException e) {
            return new ReadResult.UnexpectedFailure(e);
        }
    }

    @Override
    public AppendResult append(String streamId, Object... events) {
        return append(streamId, ExpectedStreamVersion.noStream(), events);
    }

    @Override
    public AppendResult append(String streamId, ExpectedStreamVersion expectedVersion, Object... events) {
        try (var connection = dataSource.getConnection()) {
            return appendIn(connection, streamId, expectedVersion, events);
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

    AppendResult appendIn(
            Connection connection, String streamId, ExpectedStreamVersion expectedVersion, Object[] events)
            throws SQLException {
        var values = context.inherit();

        try (var statement = connection.prepareStatement(APPEND)) {
            statement.setArray(
                    1, textArray(connection, events, event -> UUID.randomUUID().toString()));
            statement.setArray(2, textArray(connection, events, EventSerializer.mapper::writeValueAsString));
            statement.setArray(
                    3,
                    textArray(
                            connection,
                            events,
                            event -> EventSerializer.mapper.writeValueAsString(metadataOf(values))));
            statement.setArray(
                    4,
                    connection.createArrayOf(
                            "int4", Arrays.stream(events).map(event -> 1).toArray()));
            statement.setArray(5, textArray(connection, events, event -> EventTypeMapper.toName(event.getClass())));
            statement.setArray(6, textArray(connection, events, event -> "E"));
            statement.setString(7, streamId);
            statement.setString(8, streamTypeOf(streamId));
            statement.setString(9, expectedKindOf(expectedVersion));
            statement.setLong(10, expectedPositionOf(expectedVersion));

            try (var rows = statement.executeQuery()) {
                rows.next();

                var streamPosition = rows.getLong("stream_position");

                if (!rows.getBoolean("success")) return failedAppend(expectedVersion, streamPosition);

                var globalPositions = (Long[]) rows.getArray("global_positions").getArray();

                return new AppendResult.Success(
                        streamPosition,
                        new Checkpoint(rows.getLong("transaction_id"), globalPositions[globalPositions.length - 1]));
            }
        }
    }

    private static AppendResult failedAppend(ExpectedStreamVersion expectedVersion, long actual) {
        return expectedVersion instanceof ExpectedStreamVersion.NoStream
                ? new AppendResult.StreamAlreadyExists(actual)
                : new AppendResult.Conflict(expectedVersion, actual);
    }

    private static String streamTypeOf(String streamId) {
        var separator = streamId.indexOf('-');

        return separator < 0 ? streamId : streamId.substring(0, separator);
    }

    private static String expectedKindOf(ExpectedStreamVersion expectedVersion) {
        return switch (expectedVersion) {
            case ExpectedStreamVersion.NoStream ignored -> "no_stream";
            case ExpectedStreamVersion.Any ignored -> "any";
            case ExpectedStreamVersion.StreamExists ignored -> "stream_exists";
            case ExpectedStreamVersion.At ignored -> "at";
        };
    }

    private static long expectedPositionOf(ExpectedStreamVersion expectedVersion) {
        return expectedVersion instanceof ExpectedStreamVersion.At at ? at.version() : -1;
    }

    private static Array textArray(Connection connection, Object[] events, Function<Object, String> valueOf)
            throws SQLException {
        return connection.createArrayOf(
                "text", Arrays.stream(events).map(valueOf).toArray());
    }

    private static Map<String, String> metadataOf(MessageValues values) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put(MessageContext.CORRELATION_ID, values.correlationId());
        metadata.put(MessageContext.CAUSATION_ID, values.causationId());
        if (values.traceId() != null) metadata.put(TRACE_ID, values.traceId());
        if (values.spanId() != null) metadata.put(SPAN_ID, values.spanId());

        return metadata;
    }

    static EventEnvelope<Object> eventOf(ResultSet row) throws SQLException {
        var type = row.getString("message_type");
        var eventClass = EventTypeMapper.toClass(type)
                .orElseThrow(() -> new UnreadableMessageException(type, "no class is mapped to this type name"));

        try {
            var messageId = row.getString("message_id");
            var metadata = EventSerializer.mapper.readValue(row.getString("message_metadata"), METADATA_TYPE);
            var correlationId = metadata.getOrDefault(MessageContext.CORRELATION_ID, messageId);

            Object event = EventSerializer.mapper.readValue(row.getString("message_data"), eventClass);

            return new EventEnvelope<>(
                    event,
                    new EventMetadata(
                            row.getString("stream_id"),
                            row.getLong("stream_position"),
                            row.getLong("global_position"),
                            row.getLong("transaction_id"),
                            messageId,
                            correlationId,
                            metadata.getOrDefault(MessageContext.CAUSATION_ID, correlationId),
                            metadata.get(TRACE_ID),
                            metadata.get(SPAN_ID)));
        } catch (JacksonException e) {
            throw new UnreadableMessageException(type, e);
        }
    }

    private static final String TRACE_ID = "traceId";
    private static final String SPAN_ID = "spanId";
    private static final TypeReference<Map<String, String>> METADATA_TYPE = new TypeReference<>() {};

    private final DataSource dataSource;
    private final HikariDataSource ownedDataSource;
    private final MessageContext context;
    private final SubscriptionCheckpoints checkpoints;
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
            SubscriptionCheckpoints checkpoints,
            BackOff backOff,
            Duration pollInterval) {
        this(dataSource, null, context, checkpoints, backOff, pollInterval);
    }

    public static PostgreSQLEventStore connectingTo(
            String url,
            String username,
            String password,
            MessageContext context,
            SubscriptionCheckpoints checkpoints,
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
            SubscriptionCheckpoints checkpoints,
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
