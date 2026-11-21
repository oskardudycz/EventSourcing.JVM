package io.eventdriven.distributedprocesses.core.eventstore.postgresql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.zaxxer.hikari.HikariDataSource;
import io.eventdriven.distributedprocesses.core.eventstore.DurableEventStoreTests;
import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.eventstore.EventStore.AppendResult;
import io.eventdriven.distributedprocesses.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.distributedprocesses.core.processing.RetryBackOff;
import io.eventdriven.distributedprocesses.core.serialization.EventTypeMapper;
import io.eventdriven.distributedprocesses.core.subscriptions.SubscriptionCheckpoints;
import io.eventdriven.testing.ProjectionsDatabase;
import io.eventdriven.testing.TestTracing;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

public class PostgreSQLEventStoreTests extends DurableEventStoreTests {
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private final TestTracing tracing = new TestTracing();
    private static final HikariDataSource dataSource = PostgreSQLTestDatabase.dataSource();
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
    protected void appendEventOfUnknownType(String streamId, String typeName) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("""
                        select success from append_to_stream(
                            array[?::text], array['{}'::text], array['{}'::text], array[1],
                            array[?::text], array['E'::text], ?, 'ShoppingCart', 'no_stream', -1)
                        """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, typeName);
            statement.setString(3, streamId);
            statement.executeQuery();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterEach
    void closeStores() {
        stores.forEach(PostgreSQLEventStore::close);
    }

    @AfterAll
    static void closeDataSource() {
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
            store.appendIn(
                    slow, streamId, ExpectedStreamVersion.noStream(), new Object[] {new ItemAdded(cartId, "t-shirt")});
            store.appendIn(
                    fast, otherStreamId, ExpectedStreamVersion.noStream(), new Object[] {new ItemAdded(cartId, "hoodie")
                    });
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
    public void appendingThreeEventsWritesOneStreamRowAndThreeMessageRowsInOneTransaction() {
        var orderId = UUID.randomUUID();
        var orderStreamId = "Order-%s".formatted(orderId);
        var jdbc = new JdbcTemplate(dataSource);

        var result = eventStore.append(
                orderStreamId,
                new ItemAdded(cartId, "t-shirt"),
                new ItemAdded(cartId, "hoodie"),
                new ItemAdded(cartId, "socks"));

        assertThat(result.succeeded()).isTrue();
        assertThat(jdbc.queryForMap(
                        "select stream_type, stream_position from streams where stream_id = ?", orderStreamId))
                .containsEntry("stream_type", "Order")
                .containsEntry("stream_position", 2L);
        var messages = jdbc.queryForList("""
                select stream_position, message_kind, message_type, message_schema_version,
                       transaction_id::text as transaction_id, global_position
                from messages where stream_id = ? order by stream_position
                """, orderStreamId);
        assertThat(messages)
                .extracting(message -> message.get("stream_position"))
                .containsExactly(0L, 1L, 2L);
        assertThat(messages).extracting(message -> message.get("message_kind")).containsOnly("E");
        assertThat(messages)
                .extracting(message -> message.get("message_type"))
                .containsOnly(EventTypeMapper.toName(ItemAdded.class));
        assertThat(messages)
                .extracting(message -> message.get("message_schema_version"))
                .containsOnly(1);
        assertThat(messages)
                .extracting(message -> message.get("transaction_id"))
                .containsOnly(String.valueOf(
                        ((AppendResult.Success) result).lastEvent().transactionId()));
        assertThat(messages.stream()
                        .map(message -> (Long) message.get("global_position"))
                        .toList())
                .isSorted()
                .doesNotHaveDuplicates();
    }

    @Test
    public void aFailedVersionCheckWritesNoMessageRows() {
        eventStore.append(streamId, new ItemAdded(cartId, "t-shirt"));
        var jdbc = new JdbcTemplate(dataSource);

        var result = eventStore.append(streamId, ExpectedStreamVersion.at(5), new ItemAdded(cartId, "hoodie"));

        assertThat(result.succeeded()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from messages where stream_id = ?", Long.class, streamId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("select stream_position from streams where stream_id = ?", Long.class, streamId))
                .isEqualTo(0L);
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
                projections.bean(SubscriptionCheckpoints.class),
                RetryBackOff.exponential(Duration.ofMillis(100)),
                pollInterval);
        stores.add(store);

        return store;
    }
}
