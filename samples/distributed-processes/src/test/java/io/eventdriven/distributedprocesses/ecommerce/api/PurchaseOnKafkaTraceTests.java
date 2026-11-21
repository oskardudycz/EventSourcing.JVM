package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.messaging.KafkaMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageMapping;
import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.OrderCanceled;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.OrderPaymentFailed;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.OrderAwaitingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.OrdersAwaitingPayment;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.UnpaidOrdersWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.PaymentAuthorized;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PaymentTimeoutWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPayments;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.ConfirmPackageSent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shipments.sandbox.SandboxFulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external.ShoppingCartExternalEvent;
import io.eventdriven.testing.InMemorySpansConfiguration;
import io.eventdriven.testing.KafkaTopics;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestMessages;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

@DirtiesContext
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "messaging.bus=kafka",
            "eventstore.type=kurrentdb",
            "projections.database=postgresql",
            "messaging.retry.initial-interval=100ms"
        })
@AutoConfigureTracing
@Import({
    InMemorySpansConfiguration.class,
    TestConfigurations.MovableClock.class,
    PurchaseOnKafkaTraceTests.HoldableFulfillment.class
})
public class PurchaseOnKafkaTraceTests extends PurchaseTraceTests {
    private static final String KAFKA = TestDatabases.kafkaBootstrapServers();

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        var postgreSQL = TestDatabases.newPostgreSQLDatabase();

        registry.add("spring.kafka.bootstrap-servers", () -> KAFKA);
        registry.add("kurrentdb.connectionstring", TestDatabases::kurrentDBConnectionString);
        TestDatabases.addProjectionsDatabase(registry, postgreSQL);
    }

    @Autowired
    private MessageBus messageBus;

    @Autowired
    private Tracer tracer;

    @Autowired
    private HoldableFulfillmentService fulfillment;

    @Autowired
    private OrderAwaitingPaymentRepository orderAwaitingPaymentRepository;

    @Autowired
    private PendingPaymentRepository pendingPaymentRepository;

    @Autowired
    private TestConfigurations.Clock clock;

    @Test
    public void checkoutStartsTheTraceAndEveryFollowingMessageCarriesItsCorrelationId() {
        var order = checkedOutOrder();

        var checkout = lastOf(cartStreamOf(order));
        var addedProducts = eventsOf(cartStreamOf(order)).stream()
                .map(EventEnvelope::metadata)
                .filter(metadata -> !metadata.messageId().equals(checkout.messageId()))
                .toList();
        await().atMost(TIMEOUT).until(() -> !eventsOf(paymentStreamOf(order)).isEmpty());

        assertThat(addedProducts).isNotEmpty().allSatisfy(metadata -> {
            assertThat(metadata.correlationId()).isNotEqualTo(checkout.correlationId());
            assertThat(metadata.traceId()).isNotEqualTo(checkout.traceId());
        });
        assertThat(metadataOf(orderStreamOf(order)))
                .isNotEmpty()
                .allSatisfy(metadata -> assertThat(metadata.correlationId()).isEqualTo(checkout.correlationId()));
        assertThat(metadataOf(paymentStreamOf(order)))
                .isNotEmpty()
                .allSatisfy(metadata -> assertThat(metadata.correlationId()).isEqualTo(checkout.correlationId()));
    }

    @Test
    public void placeOrderHasTheCheckedOutMessageAsItsCause() {
        var order = checkedOutOrder();

        var placeOrder = awaitRecord(OrdersConfig.internal, order.orderId(), OrderCommand.PlaceOrder.class.getName());
        var checkedOut = awaitRecord(
                ShoppingCartsConfig.external,
                order.orderId(),
                ShoppingCartExternalEvent.ShoppingCartCheckedOut.class.getName());

        assertThat(header(placeOrder, "causationId")).isEqualTo(header(checkedOut, "messageId"));
        assertThat(header(placeOrder, "correlationId")).isEqualTo(header(checkedOut, "correlationId"));
        assertThat(traceIdOf(placeOrder)).isEqualTo(traceIdOf(checkedOut));
    }

    @Test
    public void forwarderPublishSpanIsInTheStoredEventsTrace() {
        var order = checkedOutOrder();

        var stored = lastOf(cartStreamOf(order));
        var published = awaitRecord(
                ShoppingCartsConfig.external,
                order.orderId(),
                ShoppingCartExternalEvent.ShoppingCartCheckedOut.class.getName());

        assertThat(header(published, "causationId")).isEqualTo(stored.messageId());
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems())
                        .anyMatch(span -> span.getName().contains(ShoppingCartsConfig.external)
                                && span.getTraceId().equals(stored.traceId())));
    }

    @Test
    public void confirmPaymentAuthorizationWithNoTraceContextContinuesThePurchasesTrace() {
        var order = checkedOutOrder();
        var paymentStream = paymentStreamOf(order);
        await().atMost(TIMEOUT).until(() -> !eventsOf(paymentStream).isEmpty());
        var last = lastOf(paymentStream);
        var paymentId = new PaymentId(Urns.of("ecommerce", "payment", order.orderId()));

        messageBus.publish(List.of(
                new ConfirmPaymentAuthorization(paymentId, OffsetDateTime.now().plusDays(5))));

        var authorized = awaitEventAfter(paymentStream, last, PaymentAuthorized.class);
        assertThat(authorized.correlationId()).isEqualTo(last.correlationId());
        assertThat(authorized.traceId()).isEqualTo(last.traceId());
        assertThat(stepOf("handle ConfirmPaymentAuthorization", last.traceId()).getParentSpanId())
                .isEqualTo(last.spanId());
    }

    @Test
    public void confirmPackageSentWithNoTraceContextContinuesThePurchasesTrace() {
        fulfillment.hold();
        try {
            var order = paidOrder();
            var shipmentStream = shipmentStreamOf(order);
            await().atMost(TIMEOUT).until(() -> fulfillment.requested(shipmentIdOf(order)));
            var last = lastOf(shipmentStream);

            messageBus.publish(List.of(new ConfirmPackageSent(shipmentIdOf(order), "Test Express", "TRK-1")));

            var sent = awaitEventAfter(shipmentStream, last, null);
            assertThat(sent.correlationId()).isEqualTo(last.correlationId());
            assertThat(sent.traceId()).isEqualTo(last.traceId());
            assertThat(stepOf("handle ConfirmPackageSent", last.traceId()).getParentSpanId())
                    .isEqualTo(last.spanId());
        } finally {
            fulfillment.release();
        }
    }

    @Test
    public void kurrentDBSubscriberContinuesTheTraceOfTheStoredEvent() {
        var streamId = "ShoppingCart-" + UUID.randomUUID();

        eventStore.subscribe("trace-continuation-" + UUID.randomUUID(), List.of(Opened.class), envelope -> {});
        var testSpan = tracer.nextSpan().name("test").start();
        try (var _ = tracer.withSpan(testSpan)) {
            eventStore.append(streamId, new Opened("cart"));
        } finally {
            testSpan.end();
        }

        var stored = lastOf(streamId);
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems())
                        .anyMatch(span -> span.getName().equals("handle Opened")
                                && span.getParentSpanId().equals(stored.spanId())));
    }

    record Opened(String cartId) {}

    @TestConfiguration
    static class OpenedMapping {
        @Bean
        static MessageMapping openedMapping() {
            return TestMessages.trustingPackageOf(PurchaseOnKafkaTraceTests.class);
        }
    }

    @Test
    public void workerCommandContinuesThePurchasesTrace() {
        var order = checkedOutOrder();
        var orderStream = orderStreamOf(order);
        await().atMost(TIMEOUT).until(() -> !eventsOf(paymentStreamOf(order)).isEmpty());
        var checkout = lastOf(cartStreamOf(order));
        var paymentTimeoutWorker = new PaymentTimeoutWorker(new PendingPayments(pendingPaymentRepository), messageBus);
        var unpaidOrdersWorker =
                new UnpaidOrdersWorker(new OrdersAwaitingPayment(orderAwaitingPaymentRepository), messageBus);

        clock.moveBy(Duration.ofDays(2));
        await().atMost(TIMEOUT).pollInterval(Duration.ofSeconds(1)).until(() -> {
            paymentTimeoutWorker.handle(clock.get());
            return eventsOf(orderStream).stream().anyMatch(event -> event.data() instanceof OrderPaymentFailed);
        });
        clock.moveBy(Duration.ofDays(8));
        await().atMost(TIMEOUT).pollInterval(Duration.ofSeconds(1)).until(() -> {
            unpaidOrdersWorker.handle(clock.get());
            return eventsOf(orderStream).stream().anyMatch(event -> event.data() instanceof OrderCanceled);
        });

        var closed = eventsOf(orderStream).stream()
                .filter(event -> event.data() instanceof OrderCanceled)
                .findFirst()
                .orElseThrow()
                .metadata();
        assertThat(closed.correlationId()).isEqualTo(checkout.correlationId());
        assertThat(closed.traceId()).isEqualTo(checkout.traceId());
    }

    @Test
    public void everyEventOfAPurchaseIsCausedByAPublishedMessageOrStartsItsCorrelation() {
        var order = paidOrder();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> given(() -> order.orderId())
                        .when(GET("/%s/orders".formatted(order.customerId()), JsonNode.class))
                        .then(response -> assertThat(((JsonNode) response.getResponseBody())
                                        .get("progress")
                                        .asString())
                                .isEqualTo("Completed")));

        var events = eventsOfPurchase(order);

        var knownMessageIds = new HashSet<String>();
        events.forEach(event -> knownMessageIds.add(event.messageId()));
        Stream.of(
                        OrdersConfig.internal,
                        PaymentsConfig.requests,
                        PaymentsConfig.internal,
                        ShipmentsConfig.requests,
                        ShipmentsConfig.internal,
                        ShoppingCartsConfig.external,
                        PaymentsConfig.responses,
                        ShipmentsConfig.responses)
                .flatMap(topic -> KafkaTopics.recordsOf(KAFKA, topic).stream())
                .filter(record -> new String(record.value()).contains(order.orderId()))
                .forEach(record -> knownMessageIds.add(header(record, "messageId")));
        assertThat(events)
                .allSatisfy(event -> assertThat(event.causationId())
                        .isIn(Stream.concat(knownMessageIds.stream(), Stream.of(event.correlationId()))
                                .toList()));
    }

    private PurchaseRestBuilder.BuilderResult checkedOutOrder() {
        return PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(UUID.randomUUID())
                        .withProduct(UUID.randomUUID(), 2)
                        .checkedOut());
    }

    private static String cartStreamOf(PurchaseRestBuilder.BuilderResult order) {
        return ShoppingCartFacade.mapToStreamId(ShoppingCartId.of(order.customerId(), 1));
    }

    private EventMetadata lastOf(String streamId) {
        return metadataOf(streamId).getLast();
    }

    private EventMetadata awaitEventAfter(String streamId, EventMetadata last, Class<?> type) {
        await().atMost(TIMEOUT)
                .alias("an event after position %s in %s".formatted(last.streamPosition(), streamId))
                .untilAsserted(() -> assertThat(eventsOf(streamId))
                        .anyMatch(event -> event.metadata().streamPosition() > last.streamPosition()
                                && (type == null || type.isInstance(event.data()))));

        return eventsOf(streamId).stream()
                .filter(event -> event.metadata().streamPosition() > last.streamPosition()
                        && (type == null || type.isInstance(event.data())))
                .findFirst()
                .orElseThrow()
                .metadata();
    }

    private SpanData stepOf(String name, String traceId) {
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems())
                        .anyMatch(span ->
                                span.getName().equals(name) && span.getTraceId().equals(traceId)));

        return spans.getFinishedSpanItems().stream()
                .filter(span -> span.getName().equals(name) && span.getTraceId().equals(traceId))
                .findFirst()
                .orElseThrow();
    }

    private ConsumerRecord<String, byte[]> awaitRecord(String topic, String text, String messageType) {
        await().atMost(TIMEOUT).until(() -> recordOf(topic, text, messageType) != null);

        return recordOf(topic, text, messageType);
    }

    private ConsumerRecord<String, byte[]> recordOf(String topic, String text, String messageType) {
        return KafkaTopics.recordsOf(KAFKA, topic).stream()
                .filter(record -> new String(record.value()).contains(text)
                        && messageType.equals(header(record, KafkaMessageBus.TYPE_ID)))
                .findFirst()
                .orElse(null);
    }

    private static String header(ConsumerRecord<String, byte[]> record, String name) {
        return KafkaTopics.header(record, name);
    }

    private static String traceIdOf(ConsumerRecord<String, byte[]> record) {
        return header(record, "traceparent").split("-")[1];
    }

    public static class HoldableFulfillmentService implements FulfillmentService {
        private final FulfillmentService sandbox;
        private final Set<ShipmentId> requests = ConcurrentHashMap.newKeySet();
        private volatile boolean holding;

        HoldableFulfillmentService(FulfillmentService sandbox) {
            this.sandbox = sandbox;
        }

        void hold() {
            holding = true;
        }

        void release() {
            holding = false;
        }

        boolean requested(ShipmentId shipmentId) {
            return requests.contains(shipmentId);
        }

        @Override
        public void request(ShipmentId shipmentId, ProductItem[] productItems) {
            requests.add(shipmentId);

            if (!holding) sandbox.request(shipmentId, productItems);
        }
    }

    @TestConfiguration
    static class HoldableFulfillment {
        @Bean
        @Primary
        HoldableFulfillmentService holdableFulfillmentService(SandboxFulfillmentService sandbox) {
            return new HoldableFulfillmentService(sandbox);
        }
    }
}
