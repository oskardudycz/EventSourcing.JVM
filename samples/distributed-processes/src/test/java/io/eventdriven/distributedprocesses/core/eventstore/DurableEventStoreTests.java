package io.eventdriven.distributedprocesses.core.eventstore;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.UnreadableMessageException;
import io.eventdriven.distributedprocesses.core.subscriptions.SubscriptionCheckpoints;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatusRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatuses;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.ProjectionsDatabase;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class DurableEventStoreTests extends EventStoreTests {
    protected abstract EventStore newStore(ProjectionsDatabase projections);

    protected abstract void close(EventStore store);

    protected abstract void appendEventOfUnknownType(String streamId, String typeName);

    protected void storeCheckpointAfter(
            ProjectionsDatabase projections, String name, EventStore.AppendResult appended) {
        var checkpoint = ((EventStore.AppendResult.Success) appended).lastEvent();

        projections
                .bean(SubscriptionCheckpoints.class)
                .handle(name, checkpoint.transactionId(), checkpoint.position(), () -> {});
    }

    @Test
    public void readingAStreamWithAnUnknownTypeFailsNamingTheType() {
        var typeName = "unknown_type_%s".formatted(UUID.randomUUID().toString().replace("-", ""));
        appendEventOfUnknownType(streamId, typeName);

        assertThatThrownBy(() -> eventStore().read(streamId))
                .isInstanceOf(UnreadableMessageException.class)
                .hasMessageContaining(typeName);
    }

    @Test
    public void failedEventIsDeliveredAgainAfterARestart() {
        var name = subscriptionName();
        var store = newStore(ProjectionsDatabase.H2);
        var failures = new AtomicInteger();
        store.subscribe(name, List.of(ItemAdded.class), envelope -> {
            if (!envelope.metadata().streamId().equals(streamId)) return;

            failures.incrementAndGet();
            throw new IllegalStateException("projection is down");
        });
        store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        await().atMost(Duration.ofSeconds(10)).until(() -> failures.get() > 0);
        close(store);

        var restarted = newStore(ProjectionsDatabase.H2);
        var received = new CopyOnWriteArrayList<Object>();
        restarted.subscribe(name, List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) received.add(envelope.data());
        });

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(received).containsExactly(new ItemAdded(cartId, "t-shirt")));
    }

    @Test
    public void aSubscriptionThatFailsRepeatedlyGetsTheEventAgainUntilItSucceeds() {
        var store = newStore(ProjectionsDatabase.H2);
        var attempts = new AtomicInteger();
        var received = new CopyOnWriteArrayList<Object>();
        store.subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (!envelope.metadata().streamId().equals(streamId)) return;

            if (attempts.incrementAndGet() < 4) throw new IllegalStateException("projection is down");
            received.add(envelope.data());
        });

        store.append(streamId, new ItemAdded(cartId, "t-shirt"));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(received).containsExactly(new ItemAdded(cartId, "t-shirt")));
        assertThat(attempts).hasValue(4);
    }

    @Test
    public void closingAStoreReturnsOnlyAfterItsSubscriptionStopped() {
        var store = newStore(ProjectionsDatabase.H2);
        var attempts = new AtomicInteger();
        store.subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (!envelope.metadata().streamId().equals(streamId)) return;

            attempts.incrementAndGet();
            throw new IllegalStateException("projection is down");
        });
        store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() > 0);

        close(store);
        var attemptsAtClose = attempts.get();

        assertThat(attempts).hasValue(attemptsAtClose);
    }

    @Test
    public void closingAStoreWaitsForTheHandlerThatIsRunning() throws Exception {
        var store = newStore(ProjectionsDatabase.H2);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var handlerFinished = new AtomicBoolean(false);
        store.subscribe(subscriptionName(), List.of(ItemAdded.class), envelope -> {
            if (!envelope.metadata().streamId().equals(streamId)) return;

            entered.countDown();
            awaitUninterruptibly(release);
            handlerFinished.set(true);
        });
        store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        assertThat(entered.await(30, TimeUnit.SECONDS)).isTrue();
        var executor = Executors.newSingleThreadExecutor();

        try {
            var closing = executor.submit(() -> {
                close(store);
                return handlerFinished.get();
            });

            release.countDown();

            assertThat(closing.get(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        var interrupted = false;

        while (latch.getCount() > 0) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }

        if (interrupted) Thread.currentThread().interrupt();
    }

    @Test
    public void awaitingACheckpointHandledBeforeARestartReturnsAfterTheRestart() {
        var name = subscriptionName();
        var store = newStore(ProjectionsDatabase.H2);
        var subscription = store.subscribe(name, List.of(ItemAdded.class), envelope -> {});
        var appended = store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        var checkpoint = ((EventStore.AppendResult.Success) appended).lastEvent();
        subscription.awaitProcessed(checkpoint, PROCESSING_TIMEOUT);
        close(store);

        var restarted = newStore(ProjectionsDatabase.H2);
        var restartedSubscription = restarted.subscribe(name, List.of(ItemAdded.class), envelope -> {});

        assertThatNoException().isThrownBy(() -> restartedSubscription.awaitProcessed(checkpoint, PROCESSING_TIMEOUT));
    }

    @Test
    public void subscriptionContinuesAfterItsCheckpointOnRestart() {
        var name = subscriptionName();
        var store = newStore(ProjectionsDatabase.POSTGRESQL);
        var forwarded = new CopyOnWriteArrayList<Object>();
        var subscription = store.subscribe(name, List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) forwarded.add(envelope.data());
        });
        store.append(
                streamId,
                new ItemAdded(cartId, "t-shirt"),
                new ItemAdded(cartId, "hoodie"),
                new ItemAdded(cartId, "socks"));
        var result = store.append(streamId, ExpectedStreamVersion.any(), new ItemAdded(cartId, "scarf"));
        subscription.awaitProcessed(((EventStore.AppendResult.Success) result).lastEvent(), PROCESSING_TIMEOUT);
        close(store);
        forwarded.clear();

        var restarted = newStore(ProjectionsDatabase.POSTGRESQL);
        var restartedSubscription = restarted.subscribe(name, List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) forwarded.add(envelope.data());
        });
        var gloves = restarted.append(streamId, ExpectedStreamVersion.any(), new ItemAdded(cartId, "gloves"));

        restartedSubscription.awaitProcessed(
                ((EventStore.AppendResult.Success) gloves).lastEvent(), PROCESSING_TIMEOUT);
        assertThat(forwarded).containsExactly(new ItemAdded(cartId, "gloves"));
    }

    @Test
    public void failedCheckpointRollsBackTheProjectionsDocument() {
        var name = subscriptionName();
        var repository = ProjectionsDatabase.POSTGRESQL.bean(OrderStatusRepository.class);
        var jdbc = new JdbcTemplate(ProjectionsDatabase.POSTGRESQL.bean(DataSource.class));
        var store = newStore(ProjectionsDatabase.POSTGRESQL);
        var orderStatuses = new OrderStatuses(repository);
        var orderId = anOrderId();
        var attempts = new AtomicInteger();
        var earlierOrderId = anOrderId();
        var earlier = store.append("Order-%s".formatted(earlierOrderId.value()), orderPlaced(earlierOrderId));
        storeCheckpointAfter(ProjectionsDatabase.POSTGRESQL, name, earlier);
        store.append("Order-%s".formatted(orderId.value()), orderPlaced(orderId));
        failCheckpointStoreOf(jdbc, name);

        try {
            store.subscribe(name, orderStatuses.canHandle(), envelope -> {
                if (!isOrderOf(envelope, orderId)) return;

                orderStatuses.handle(envelope);
                attempts.incrementAndGet();
            });

            await().atMost(Duration.ofSeconds(10)).until(() -> attempts.get() >= 2);
            assertThat(repository.findById(orderId)).isEmpty();
        } finally {
            repairCheckpointStoreOf(jdbc, name);
        }

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(repository.findById(orderId)).isPresent());
    }

    @Test
    public void eventAlreadyPastTheCheckpointIsSkippedAndRolledBack() {
        var name = subscriptionName();
        var store = newStore(ProjectionsDatabase.POSTGRESQL);
        var repository = ProjectionsDatabase.POSTGRESQL.bean(OrderStatusRepository.class);
        var checkpoints = ProjectionsDatabase.POSTGRESQL.bean(SubscriptionCheckpoints.class);
        var orderStatuses = new OrderStatuses(repository);
        var warmUpOrderId = anOrderId();
        var orderId = anOrderId();
        var subscription = store.subscribe(name, orderStatuses.canHandle(), envelope -> {
            if (isOrderOf(envelope, warmUpOrderId) || isOrderOf(envelope, orderId)) orderStatuses.handle(envelope);
        });
        var warmUp = store.append("Order-%s".formatted(warmUpOrderId.value()), orderPlaced(warmUpOrderId));
        subscription.awaitProcessed(((EventStore.AppendResult.Success) warmUp).lastEvent(), PROCESSING_TIMEOUT);
        checkpoints.handle(name, Long.MAX_VALUE, Long.MAX_VALUE, () -> {});

        var appended = store.append("Order-%s".formatted(orderId.value()), orderPlaced(orderId));

        subscription.awaitProcessed(((EventStore.AppendResult.Success) appended).lastEvent(), PROCESSING_TIMEOUT);
        assertThat(repository.findById(orderId)).isEmpty();
        assertThat(checkpoints.lastProcessedPosition(name)).hasValue(Long.MAX_VALUE);
    }

    private static OrderId anOrderId() {
        return OrderId.derivedFrom(ShoppingCartId.of(UUID.randomUUID(), 1).value());
    }

    private static OrderPlaced orderPlaced(OrderId orderId) {
        var customerId = UUID.randomUUID();

        return new OrderPlaced(
                orderId,
                ShoppingCartId.of(customerId, 1),
                customerId,
                new PricedProductItem[] {new PricedProductItem(customerId, 1, 50)},
                50,
                OffsetDateTime.now());
    }

    private static boolean isOrderOf(EventEnvelope<?> envelope, OrderId orderId) {
        return envelope.metadata().streamId().equals("Order-%s".formatted(orderId.value()));
    }

    private static void failCheckpointStoreOf(JdbcTemplate jdbc, String processorId) {
        var function = functionOf(processorId);

        jdbc.execute(
                "create function %s() returns trigger as $$ begin raise exception 'checkpoint store is down'; end $$ language plpgsql"
                        .formatted(function));
        jdbc.execute(
                "create trigger %s before insert or update on processor_checkpoints for each row when (new.processor_id = '%s') execute function %s()"
                        .formatted(function, processorId, function));
    }

    private static void repairCheckpointStoreOf(JdbcTemplate jdbc, String processorId) {
        var function = functionOf(processorId);

        jdbc.execute("drop trigger %s on processor_checkpoints".formatted(function));
        jdbc.execute("drop function %s()".formatted(function));
    }

    private static String functionOf(String processorId) {
        return "fail_%s".formatted(processorId.replace("-", "_"));
    }
}
