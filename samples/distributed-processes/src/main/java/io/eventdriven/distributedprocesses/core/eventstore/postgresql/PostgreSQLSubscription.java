package io.eventdriven.distributedprocesses.core.eventstore.postgresql;

import io.eventdriven.distributedprocesses.core.eventstore.Checkpoint;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.serialization.EventTypeMapper;
import io.eventdriven.distributedprocesses.core.subscriptions.SubscriptionCheckpoints;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

final class PostgreSQLSubscription {
    private static final Logger logger = LoggerFactory.getLogger(PostgreSQLSubscription.class);
    private static final int BATCH_SIZE = 100;

    private static final String NEXT_EVENTS = """
            select %s
            from messages
            where transaction_id < pg_snapshot_xmin(pg_current_snapshot())
              and (transaction_id, global_position) > (?::text::xid8, ?)
              and message_type = any (?)
            order by transaction_id, global_position
            limit %d
            """.formatted(PostgreSQLEventStore.COLUMNS, BATCH_SIZE);

    private final DataSource dataSource;
    private final String name;
    private final List<Class<?>> types;
    private final Consumer<EventEnvelope<Object>> handler;
    private final MessageContext context;
    private final SubscriptionCheckpoints checkpoints;
    private final BackOff backOff;
    private BackOffExecution backOffExecution;
    private Checkpoint cursor;
    private Instant retryAt = Instant.MIN;
    private volatile boolean closed = false;

    PostgreSQLSubscription(
            DataSource dataSource,
            String name,
            List<Class<?>> types,
            Consumer<EventEnvelope<Object>> handler,
            MessageContext context,
            SubscriptionCheckpoints checkpoints,
            BackOff backOff) {
        this.dataSource = dataSource;
        this.name = name;
        this.types = types;
        this.handler = handler;
        this.context = context;
        this.checkpoints = checkpoints;
        this.backOff = backOff;
        this.backOffExecution = backOff.start();
    }

    void close() {
        closed = true;
    }

    void poll() {
        if (closed || Instant.now().isBefore(retryAt)) return;

        try {
            if (cursor == null) cursor = lastProcessed();

            List<EventEnvelope<Object>> batch;
            do {
                batch = nextEvents();

                for (var envelope : batch) {
                    if (closed) return;

                    handle(envelope);
                    cursor = envelope.metadata().checkpoint();
                    resetBackOff();
                }
            } while (batch.size() == BATCH_SIZE);
        } catch (SQLException | RuntimeException e) {
            fail(e);
        }
    }

    private Checkpoint lastProcessed() {
        return checkpoints.lastProcessed(name).orElse(new Checkpoint(0, 0));
    }

    private void handle(EventEnvelope<Object> envelope) {
        var metadata = envelope.metadata();

        try (var _ = context.handling(
                "handle %s".formatted(envelope.data().getClass().getSimpleName()), metadata)) {
            checkpoints.handle(name, metadata.transactionId(), metadata.logPosition(), () -> handler.accept(envelope));
        }
    }

    private List<EventEnvelope<Object>> nextEvents() throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(NEXT_EVENTS)) {
            statement.setLong(1, cursor.transactionId());
            statement.setLong(2, cursor.position());
            statement.setArray(
                    3,
                    connection.createArrayOf(
                            "text", types.stream().map(EventTypeMapper::toName).toArray()));

            var events = new ArrayList<EventEnvelope<Object>>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) events.add(PostgreSQLEventStore.eventOf(rows));
            }
            return events;
        }
    }

    private void fail(Exception cause) {
        cursor = null;
        if (closed) return;

        var delay = nextDelay();
        retryAt = Instant.now().plusMillis(delay);
        logger.error("Subscription '{}' failed; it continues from its checkpoint in {} ms", name, delay, cause);
    }

    private long nextDelay() {
        var delay = backOffExecution.nextBackOff();

        if (delay != BackOffExecution.STOP) return delay;

        backOffExecution = backOff.start();

        return backOffExecution.nextBackOff();
    }

    private void resetBackOff() {
        backOffExecution = backOff.start();
    }
}
