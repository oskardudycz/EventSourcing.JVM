package io.eventdriven.distributedprocesses.core.eventstore.postgresql;

import io.eventdriven.distributedprocesses.core.eventstore.Checkpoint;
import io.eventdriven.distributedprocesses.core.eventstore.SubscriptionProgress;
import io.eventdriven.distributedprocesses.core.eventstore.postgresql.PostgreSQLEventStore.StoredMessage;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import io.eventdriven.distributedprocesses.core.subscriptions.SubscriptionCheckpoints;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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
            order by transaction_id, global_position
            limit %d
            """.formatted(PostgreSQLEventStore.COLUMNS, BATCH_SIZE);

    private final DataSource dataSource;
    private final String name;
    private final Set<String> typeNames;
    private final Consumer<EventEnvelope<Object>> handler;
    private final MessageContext context;
    private final MessageSerializer serializer;
    private final SubscriptionCheckpoints checkpoints;
    private final BackOff backOff;
    private final SubscriptionProgress progress;
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
            MessageSerializer serializer,
            SubscriptionCheckpoints checkpoints,
            BackOff backOff) {
        this.dataSource = dataSource;
        this.name = name;
        this.typeNames = types.stream()
                .flatMap(type -> serializer.typeNamesOf(type).stream())
                .collect(Collectors.toSet());
        this.handler = handler;
        this.context = context;
        this.serializer = serializer;
        this.checkpoints = checkpoints;
        this.backOff = backOff;
        this.progress = new SubscriptionProgress(name);
        this.backOffExecution = backOff.start();
    }

    SubscriptionProgress progress() {
        return progress;
    }

    void close() {
        closed = true;
    }

    void poll() {
        if (closed || Instant.now().isBefore(retryAt)) return;

        try {
            if (cursor == null) cursor = lastProcessed();

            List<StoredMessage> batch;
            do {
                batch = nextMessages();

                for (var message : batch) {
                    if (closed) return;

                    if (typeNames.contains(message.type())) {
                        handle(PostgreSQLEventStore.eventOf(message, serializer));
                        progress.handled(message.checkpoint());
                        resetBackOff();
                    }

                    cursor = message.checkpoint();
                }
            } while (batch.size() == BATCH_SIZE);
        } catch (SQLException | RuntimeException e) {
            fail(e);
        }
    }

    private Checkpoint lastProcessed() {
        var stored = checkpoints.lastProcessed(name);
        stored.ifPresent(progress::handled);

        return stored.orElse(new Checkpoint(0, 0));
    }

    private void handle(EventEnvelope<Object> envelope) {
        var metadata = envelope.metadata();

        try (var _ = context.handling(
                "handle %s".formatted(envelope.data().getClass().getSimpleName()), metadata)) {
            checkpoints.handle(name, metadata.transactionId(), metadata.logPosition(), () -> handler.accept(envelope));
        }
    }

    private List<StoredMessage> nextMessages() throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(NEXT_EVENTS)) {
            statement.setLong(1, cursor.transactionId());
            statement.setLong(2, cursor.position());

            var messages = new ArrayList<StoredMessage>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) messages.add(StoredMessage.of(rows));
            }
            return messages;
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
