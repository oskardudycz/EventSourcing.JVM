package io.eventdriven.distributedprocesses.core.kurrentdb;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.processing.RetryBackOff;
import io.eventdriven.distributedprocesses.core.projections.ProcessorCheckpoints;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatusRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.api.OrderStatuses;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.ProjectionsDatabase;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestTracing;
import io.kurrent.dbclient.KurrentDBClient;
import io.kurrent.dbclient.KurrentDBConnectionString;
import io.kurrent.dbclient.StreamState;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class KurrentDBEventStoreTests extends DurableEventStoreTests {
    private final KurrentDBClient client =
            KurrentDBClient.create(KurrentDBConnectionString.parseOrThrow(TestDatabases.kurrentDBConnectionString()));
    private final List<KurrentDBEventStore> stores = new ArrayList<>();
    private final KurrentDBEventStore eventStore = (KurrentDBEventStore) newStore(ProjectionsDatabase.H2);

    @Override
    protected EventStore eventStore() {
        return eventStore;
    }

    @Override
    protected EventStore newStore(ProjectionsDatabase projections) {
        var store = new KurrentDBEventStore(
                client,
                TestTracing.untraced(),
                ObservationRegistry.NOOP,
                projections.bean(ProcessorCheckpoints.class),
                RetryBackOff.exponential(Duration.ofMillis(100)));
        stores.add(store);

        return store;
    }

    @Override
    protected void close(EventStore store) {
        ((KurrentDBEventStore) store).close();
    }

    @Override
    protected void storeCheckpointAfter(
            ProjectionsDatabase projections, String name, EventStore.AppendResult appended) {
        projections
                .bean(ProcessorCheckpoints.class)
                .handle(
                        name,
                        ((EventStore.AppendResult.Success) appended)
                                .logPosition()
                                .getCommitUnsigned(),
                        () -> {});
    }

    @AfterEach
    void closeStores() {
        stores.forEach(KurrentDBEventStore::close);
        client.shutdown();
    }

    @Test
    public void aCancelledSubscriptionSubscribesAgainFromItsCheckpoint() {
        var name = subscriptionName();
        var store = newStore(ProjectionsDatabase.POSTGRESQL);
        var checkpoints = ProjectionsDatabase.POSTGRESQL.bean(ProcessorCheckpoints.class);
        var earlier = store.append("ShoppingCart-%s".formatted(UUID.randomUUID()), new CartOpened(cartId));
        store.append(streamId, new ItemAdded(cartId, "t-shirt"));
        var received = new CopyOnWriteArrayList<Object>();
        checkpoints.handle(name, 1, () -> {});
        store.subscribe(name, List.of(ItemAdded.class), envelope -> {
            if (envelope.metadata().streamId().equals(streamId)) received.add(envelope.data());
        });

        await().during(Duration.ofSeconds(1))
                .atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> assertThat(received).isEmpty());

        checkpoints.handle(
                name, ((EventStore.AppendResult.Success) earlier).logPosition().getCommitUnsigned(), () -> {});

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(received).containsExactly(new ItemAdded(cartId, "t-shirt")));
    }

    @Test
    public void closingAStoreThatOwnsItsClientShutsTheClientDown() {
        var store = KurrentDBEventStore.connectingTo(
                TestDatabases.kurrentDBConnectionString(),
                TestTracing.untraced(),
                ObservationRegistry.NOOP,
                ProjectionsDatabase.H2.bean(ProcessorCheckpoints.class),
                RetryBackOff.exponential(Duration.ofMillis(100)));
        assertThat(store.append(streamId, new CartOpened(cartId)).succeeded()).isTrue();

        store.close();

        assertThat(store.append(streamId, StreamState.any(), new ItemAdded(cartId, "t-shirt")))
                .isInstanceOf(EventStore.AppendResult.UnexpectedFailure.class);
    }

    @Test
    public void aProjectionGetsItsEventsInStreamOrderOnKurrentDB() {
        var repository = ProjectionsDatabase.H2.bean(OrderStatusRepository.class);
        var orderStatuses = new OrderStatuses(repository);
        eventStore.subscribe(subscriptionName(), orderStatuses.canHandle(), orderStatuses::handle);

        var customerId = UUID.randomUUID();
        var cartId = ShoppingCartId.of(customerId, 1);
        var orderId = OrderId.derivedFrom(cartId.value());
        var paymentId = PaymentId.derivedFrom(orderId.value(), 2);
        var now = OffsetDateTime.now();

        eventStore.append(
                "Order-%s".formatted(orderId.value()),
                new OrderPlaced(
                        orderId,
                        cartId,
                        customerId,
                        new PricedProductItem[] {new PricedProductItem(customerId, 1, 50)},
                        50,
                        now),
                new OrderPaymentAuthorized(orderId, paymentId, now));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var status = assertThat(repository.findById(orderId)).get();

            status.extracting(OrderStatus::getVersion).isEqualTo(1L);
            status.extracting(OrderStatus::getPaymentId).isEqualTo(paymentId);
        });
    }
}
