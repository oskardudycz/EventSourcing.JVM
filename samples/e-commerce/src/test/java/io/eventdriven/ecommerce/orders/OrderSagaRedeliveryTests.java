package io.eventdriven.ecommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.EventMetadata;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.payments.PaymentCommand.AuthorizePayment;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.ReserveStock;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class OrderSagaRedeliveryTests {
    private final TestTracing tracing = new TestTracing();
    private final InMemoryEventStore eventStore =
            new InMemoryEventStore(tracing.messageContext(), ECommerceMessages.serializer());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(
            tracing.messageContext(),
            List.of(new MessageRoutes().routeTarget(MessageRoutes.inPackage("io.eventdriven.ecommerce"), "published")),
            List.of(new MessageChannel("published")),
            List.of(new MessageQueue("probe", "published", "#")));
    private final AtomicInteger reserveStockCalls = new AtomicInteger();

    private final ShoppingCart.Id cartId = ShoppingCart.Id.of(UUID.randomUUID());
    private final Order.Id orderId = Order.Id.derivedFrom(cartId.value());
    private final String authorizationsStream = "Probe-authorizations-" + UUID.randomUUID();
    private final String reservationsStream = "Probe-reservations-" + UUID.randomUUID();

    public OrderSagaRedeliveryTests() {
        messageBus
                .subscribe("probe", AuthorizePayment.class, command -> {
                    eventStore.append(
                            authorizationsStream,
                            ExpectedStreamVersion.any(),
                            new Handled(command.paymentId().value()));
                })
                .subscribe("probe", ReserveStock.class, command -> {
                    eventStore.append(
                            reservationsStream, ExpectedStreamVersion.any(), new Handled(command.referenceId()));

                    if (reserveStockCalls.incrementAndGet() == 1) throw new IllegalStateException("Warehouse is busy");
                });

        var saga = new OrderSaga(messageBus);
        eventStore.subscribe(
                "OrderPlaced",
                List.of(OrderEvent.OrderPlaced.class),
                envelope -> saga.onOrderPlaced((OrderEvent.OrderPlaced) envelope.data()));
    }

    @Test
    public void sagaRepublishesBothCommandsWithTheSameIdsOnRedelivery() {
        eventStore.append(
                "Order-" + UUID.randomUUID(),
                new OrderEvent.OrderPlaced(
                        orderId,
                        cartId,
                        UUID.randomUUID(),
                        new Order.PricedProductItem[] {new Order.PricedProductItem(UUID.randomUUID(), 2, 12.5)},
                        25,
                        OffsetDateTime.now()));
        eventStore.append("Trigger-" + UUID.randomUUID(), new Handled("trigger"));

        var authorizations = causationsOf(authorizationsStream);
        var reservations = causationsOf(reservationsStream);

        assertThat(authorizations).hasSize(2).containsOnly(authorizations.getFirst());
        assertThat(reservations).hasSize(2).containsOnly(reservations.getFirst());
        assertThat(authorizations.getFirst()).isNotEqualTo(reservations.getFirst());
    }

    private List<String> causationsOf(String streamId) {
        var result = (EventStore.ReadResult.Success) eventStore.read(streamId);

        return result.events().stream()
                .map(EventEnvelope::metadata)
                .map(EventMetadata::causationId)
                .toList();
    }

    record Handled(String about) {}
}
