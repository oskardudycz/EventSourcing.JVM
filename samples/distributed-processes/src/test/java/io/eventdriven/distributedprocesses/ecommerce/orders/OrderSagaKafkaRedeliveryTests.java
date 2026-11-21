package io.eventdriven.distributedprocesses.ecommerce.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.eventstore.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.messaging.KafkaMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageChannel;
import io.eventdriven.distributedprocesses.core.messaging.MessageContext;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.AuthorizePayment;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.ReserveStock;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.testing.InMemorySpansConfiguration;
import io.eventdriven.testing.KafkaTopics;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestKafka;
import io.eventdriven.testing.TestMessages;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
public class OrderSagaKafkaRedeliveryTests {
    private final String bootstrapServers = TestDatabases.kafkaBootstrapServers();
    private final String published = "published-" + UUID.randomUUID().toString().substring(0, 8);
    private final ShoppingCartId cartId = ShoppingCartId.of(UUID.randomUUID());
    private final OrderId orderId = OrderId.derivedFrom(cartId.value());

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ObservationRegistry observations;

    private KafkaMessageBus messageBus;

    @AfterEach
    void closeMessageBus() {
        messageBus.close();
        KafkaTopics.deleteContaining(bootstrapServers, published);
    }

    @Test
    public void sagaRepublishesBothCommandsWithTheSameIdsOnRedelivery() {
        var eventStore = new InMemoryEventStore(messageContext, TestMessages.serializer());
        messageBus = KafkaMessageBus.connectingTo(
                bootstrapServers,
                messageContext,
                observations,
                TestMessages.serializer(),
                Duration.ofSeconds(10),
                TestKafka.failureHandling(bootstrapServers, observations),
                List.of(new MessageRoutes()
                        .routeTarget(
                                MessageRoutes.inPackage("io.eventdriven.distributedprocesses.ecommerce"), published)),
                List.of(new MessageChannel(published)),
                List.of());
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

        var records = recordsOnTheChannel(4);
        var authorizations = messageIdsOf(records, AuthorizePayment.class);
        var reservations = messageIdsOf(records, ReserveStock.class);
        assertThat(authorizations).hasSize(2).containsOnly(authorizations.getFirst());
        assertThat(reservations).hasSize(2).containsOnly(reservations.getFirst());
        assertThat(authorizations.getFirst()).isNotEqualTo(reservations.getFirst());
    }

    record Trigger() {}

    private List<ConsumerRecord<String, byte[]>> recordsOnTheChannel(int count) {
        await().atMost(Duration.ofSeconds(30))
                .until(() -> KafkaTopics.recordsOf(bootstrapServers, published).size() >= count);

        return KafkaTopics.recordsOf(bootstrapServers, published);
    }

    private List<String> messageIdsOf(List<ConsumerRecord<String, byte[]>> records, Class<?> messageClass) {
        var typeName = messageClass.getName();

        return records.stream()
                .filter(record -> typeName.equals(KafkaTopics.header(record, KafkaMessageBus.TYPE_ID)))
                .map(record -> KafkaTopics.header(record, KafkaMessageBus.MESSAGE_ID))
                .toList();
    }
}
