package io.eventdriven.ecommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.payments.PaymentCommand.AuthorizePayment;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.ReserveStock;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("in-memory")
@AutoConfigureTracing
@Import(InMemorySpansConfiguration.class)
public abstract class OrderSagaBrokerRedeliveryTests {
    protected final String published =
            "published-" + UUID.randomUUID().toString().substring(0, 8);
    private final ShoppingCart.Id cartId = ShoppingCart.Id.of(UUID.randomUUID());
    private final Order.Id orderId = Order.Id.derivedFrom(cartId.value());

    @Autowired
    protected MessageContext messageContext;

    protected abstract MessageBus openBus(List<MessageRoutes> routes, List<MessageChannel> channels);

    protected abstract List<PublishedMessage> awaitPublished(int count);

    protected abstract void closeBusAndDeleteChannels();

    @AfterEach
    void cleanUp() {
        closeBusAndDeleteChannels();
    }

    @Test
    public void sagaRepublishesBothCommandsWithTheSameIdsOnRedelivery() {
        var eventStore = new InMemoryEventStore(messageContext, ECommerceMessages.serializer());
        var messageBus = openBus(
                List.of(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage("io.eventdriven.ecommerce"), published)),
                List.of(new MessageChannel(published)));
        var saga = new OrderSaga(messageBus);
        var calls = new AtomicInteger();
        eventStore.subscribe("OrderSaga", List.of(OrderEvent.OrderPlaced.class), envelope -> {
            saga.onOrderPlaced((OrderEvent.OrderPlaced) envelope.data());

            if (calls.incrementAndGet() == 1) throw new IllegalStateException("The process crashed after publishing");
        });

        eventStore.append(
                "Order-" + UUID.randomUUID(),
                new OrderEvent.OrderPlaced(
                        orderId,
                        cartId,
                        UUID.randomUUID(),
                        new Order.PricedProductItem[] {new Order.PricedProductItem(UUID.randomUUID(), 2, 12.5)},
                        25,
                        OffsetDateTime.now()));
        eventStore.append("Trigger-" + UUID.randomUUID(), new Trigger());

        var messages = awaitPublished(4);
        var authorizations = messageIdsOf(messages, AuthorizePayment.class);
        var reservations = messageIdsOf(messages, ReserveStock.class);
        assertThat(authorizations).hasSize(2).containsOnly(authorizations.getFirst());
        assertThat(reservations).hasSize(2).containsOnly(reservations.getFirst());
        assertThat(authorizations.getFirst()).isNotEqualTo(reservations.getFirst());
    }

    protected record PublishedMessage(String typeName, String messageId) {}

    record Trigger() {}

    private static List<String> messageIdsOf(List<PublishedMessage> messages, Class<?> messageClass) {
        return messages.stream()
                .filter(message -> messageClass.getName().equals(message.typeName()))
                .map(PublishedMessage::messageId)
                .toList();
    }
}
