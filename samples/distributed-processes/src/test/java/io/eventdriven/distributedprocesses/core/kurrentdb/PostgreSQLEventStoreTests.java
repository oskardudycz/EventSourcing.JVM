package io.eventdriven.distributedprocesses.core.kurrentdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.zaxxer.hikari.HikariDataSource;
import io.eventdriven.distributedprocesses.core.processing.RetryBackOff;
import io.eventdriven.distributedprocesses.core.projections.ProcessorCheckpoints;
import io.eventdriven.distributedprocesses.core.serialization.EventTypeMapper;
import io.eventdriven.testing.ProjectionsDatabase;
import io.eventdriven.testing.TestTracing;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostgreSQLEventStoreTests extends DurableEventStoreTests {
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private final TestTracing tracing = new TestTracing();
    private final HikariDataSource dataSource = PostgreSQLTestDatabase.dataSource();
    private final List<PostgreSQLEventStore> stores = new ArrayList<>();
    private final PostgreSQLEventStore eventStore = newStore(ProjectionsDatabase.H2, POLL_INTERVAL);

    @Override
    protected EventStore eventStore() {
        return eventStore;
    }

    @Override
    protected EventStore newStore(ProjectionsDatabase projections) {
        return newStore(projections, POLL_INTERVAL);
    }

    @Override
    protected void close(EventStore store) {
        ((PostgreSQLEventStore) store).close();
    }

    @Override
    protected void storeCheckpointAfter(
            ProjectionsDatabase projections, String name, EventStore.AppendResult appended) {
        var position =
                ((EventStore.AppendResult.Success) appended).logPosition().getCommitUnsigned();
        var transactionId = new JdbcTemplate(dataSource)
                .queryForObject("select transaction_id from events where global_position = ?", Long.class, position);

        projections.bean(ProcessorCheckpoints.class).handle(name, transactionId, position, () -> {});
    }

    @AfterEach
    void closeStores() {
        stores.forEach(PostgreSQLEventStore::close);
        dataSource.close();
    }

    @Test
    public void eventOfATransactionThatCommitsLastIsNotSkipped() throws SQLException {
        var store = newStore(ProjectionsDatabase.H2, Duration.ofDays(1));
        var otherStreamId = "ShoppingCart-%s".formatted(UUID.randomUUID());
        var received = new CopyOnWriteArrayList<Object>();
        store.subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            var streamOfEvent = envelope.metadata().streamId();
            if (streamOfEvent.equals(streamId) || streamOfEvent.equals(otherStreamId)) received.add(envelope.data());
        });

        try (var slow = dataSource.getConnection();
                var fast = dataSource.getConnection()) {
            slow.setAutoCommit(false);
            fast.setAutoCommit(false);
            insertItemAdded(slow, streamId, "t-shirt");
            insertItemAdded(fast, otherStreamId, "hoodie");
            fast.commit();

            store.poll();

            assertThat(received).isEmpty();

            slow.commit();

            store.poll();

            assertThat(received).containsExactly(new ItemAdded(cartId, "t-shirt"), new ItemAdded(cartId, "hoodie"));
        }
    }

    @Test
    public void parallelAppendsToTwoStreamsBothSucceed() throws Exception {
        var otherStreamId = "ShoppingCart-%s".formatted(UUID.randomUUID());
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> {
                release.await();
                return eventStore.append(streamId, new ItemAdded(cartId, "t-shirt"));
            });
            var second = executor.submit(() -> {
                release.await();
                return eventStore.append(otherStreamId, new ItemAdded(cartId, "hoodie"));
            });
            release.countDown();

            assertThat(first.get().succeeded()).isTrue();
            assertThat(second.get().succeeded()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void subscriberContinuesTheTraceOfTheStoredEvent() {
        var store = newStore(ProjectionsDatabase.H2, POLL_INTERVAL);
        var handlerSpan = new AtomicReference<io.micrometer.tracing.TraceContext>();
        store.subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId))
                handlerSpan.set(tracing.tracer().currentSpan().context());
        });
        var appendSpan = tracing.tracer().nextSpan().name("append").start();

        try (var _ = tracing.tracer().withSpan(appendSpan)) {
            store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        } finally {
            appendSpan.end();
        }

        await().atMost(Duration.ofSeconds(10)).until(() -> handlerSpan.get() != null);
        assertThat(handlerSpan.get().traceId()).isEqualTo(appendSpan.context().traceId());
        assertThat(handlerSpan.get().parentId()).isEqualTo(appendSpan.context().spanId());
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(tracing.finishedSpans()).anySatisfy(span -> {
                    assertThat(span.getName()).isEqualTo("handle ItemAdded");
                    assertThat(span.getTraceId()).isEqualTo(appendSpan.context().traceId());
                }));
    }

    private PostgreSQLEventStore newStore(ProjectionsDatabase projections, Duration pollInterval) {
        var store = new PostgreSQLEventStore(
                dataSource,
                tracing.messageContext(),
                projections.bean(ProcessorCheckpoints.class),
                RetryBackOff.exponential(Duration.ofMillis(100)),
                pollInterval);
        stores.add(store);

        return store;
    }

    private void insertItemAdded(Connection connection, String streamId, String productId) throws SQLException {
        try (var insertStream = connection.prepareStatement("insert into streams(id, stream_position) values (?, 0)");
                var insertEvent = connection.prepareStatement(
                        "insert into events(stream_id, stream_position, id, type, data) values (?, 0, ?, ?, ?::jsonb)")) {
            insertStream.setString(1, streamId);
            insertStream.executeUpdate();

            insertEvent.setString(1, streamId);
            insertEvent.setObject(2, UUID.randomUUID());
            insertEvent.setString(3, EventTypeMapper.toName(ItemAdded.class));
            insertEvent.setString(4, "{\"cartId\":\"%s\",\"productId\":\"%s\"}".formatted(cartId, productId));
            insertEvent.executeUpdate();
        }
    }
}
