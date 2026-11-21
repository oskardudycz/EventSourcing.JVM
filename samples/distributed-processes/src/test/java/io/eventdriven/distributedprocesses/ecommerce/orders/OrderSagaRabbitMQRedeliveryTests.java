package io.eventdriven.distributedprocesses.ecommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.kurrentdb.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.core.messaging.RabbitMQMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.ECommerceMessages;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.AuthorizePayment;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.ReserveStock;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.InMemorySpansConfiguration;
import io.eventdriven.testing.RabbitMQConnection;
import io.eventdriven.testing.RabbitMQManagement;
import io.eventdriven.testing.RabbitMQQueues;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestMessages;
import io.eventdriven.testing.TestRabbitMQ;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("in-memory")
@AutoConfigureTracing
@Import(InMemorySpansConfiguration.class)
public class OrderSagaRabbitMQRedeliveryTests {
    private final String published = "published-" + UUID.randomUUID().toString().substring(0, 8);
    private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
    private final OrderId orderId = OrderId.derivedFrom(cartId.value());
    private final RabbitMQConnection connection = TestDatabases.rabbitMQ();

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ApplicationContext applicationContext;

    private RabbitMQMessageBus messageBus;
    private RabbitMQQueues queues;

    @AfterEach
    void cleanUp() {
        messageBus.close();
        queues.close();
        new RabbitMQManagement(connection).deleteContaining(published);
    }

    @Test
    public void sagaRepublishesBothCommandsWithTheSameIdsOnRedelivery() {
        var eventStore = new InMemoryEventStore(messageContext);
        messageBus = TestRabbitMQ.busOn(
                connection, messageContext, applicationContext, TestMessages.serializer(ECommerceMessages.mapping()));
        messageBus
                .routes(new MessageRoutes()
                        .routeTarget(
                                MessageRoutes.inPackage("io.eventdriven.distributedprocesses.ecommerce"), published))
                .channel(published);
        queues = new RabbitMQQueues(connection);
        var collector = queues.collectEverythingPublishedTo(published);
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
                        new PricedProductItem[] {new PricedProductItem(UUID.randomUUID(), 2, 12.5)},
                        25,
                        OffsetDateTime.now()));
        eventStore.append("Trigger-" + UUID.randomUUID(), new Trigger());

        var messages = queues.receiveAll(collector, 4);
        var authorizations = messageIdsOf(messages, AuthorizePayment.class);
        var reservations = messageIdsOf(messages, ReserveStock.class);
        assertThat(authorizations).hasSize(2).containsOnly(authorizations.getFirst());
        assertThat(reservations).hasSize(2).containsOnly(reservations.getFirst());
        assertThat(authorizations.getFirst()).isNotEqualTo(reservations.getFirst());
    }

    record Trigger() {}

    private List<String> messageIdsOf(List<Message> messages, Class<?> messageClass) {
        var typeName = ECommerceMessages.mapping().typeNameOf(messageClass).orElseThrow();

        return messages.stream()
                .map(Message::getMessageProperties)
                .filter(properties -> typeName.equals(properties.getHeader(RabbitMQMessageBus.MESSAGE_TYPE)))
                .map(properties -> properties.getMessageId())
                .toList();
    }
}
