package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.OrderCanceled;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.OrderPaymentFailed;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.OrderAwaitingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.OrdersAwaitingPayment;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.UnpaidOrdersWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.CheckoutSessionCreated;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.PaymentAuthorized;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentFacade;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PaymentTimeoutWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPaymentRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PendingPayments;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.ConfirmPackageSent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentFacade;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shipments.sandbox.SandboxFulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartFacade;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartsConfig;
import io.eventdriven.testing.ApiSpecification;
import io.eventdriven.testing.InMemorySpansConfiguration;
import io.eventdriven.testing.KafkaTopics;
import io.eventdriven.testing.TestDatabases;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
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
public class PurchaseOnKafkaTraceTests extends ApiSpecification {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);
    private static final String KAFKA = TestDatabases.kafkaBootstrapServers();

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        var postgreSQL = TestDatabases.newPostgreSQLDatabase();

        registry.add("spring.kafka.bootstrap-servers", () -> KAFKA);
        registry.add("kurrentdb.connectionstring", TestDatabases::kurrentDBConnectionString);
        registry.add("spring.datasource.url", postgreSQL::url);
        registry.add("spring.datasource.username", postgreSQL::username);
        registry.add("spring.datasource.password", postgreSQL::password);
    }

    @Autowired
    private EventStore eventStore;

    @Autowired
    private MessageBus messageBus;

    @Autowired
    private Tracer tracer;

    @Autowired
    private InMemorySpanExporter spans;

    @Autowired
    private HoldableFulfillmentService fulfillment;

    @Autowired
    private OrderAwaitingPaymentRepository orderAwaitingPaymentRepository;

    @Autowired
    private PendingPaymentRepository pendingPaymentRepository;

    @Autowired
    private TestConfigurations.Clock clock;

    public PurchaseOnKafkaTraceTests() {
        super("customers");
    }

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

        var placeOrder = awaitRecord(OrdersConfig.internal, order.orderId(), "orders.PlaceOrder");
        var checkedOut =
                awaitRecord(ShoppingCartsConfig.external, order.orderId(), "shopping_carts.ShoppingCartCheckedOut");

        assertThat(header(placeOrder, "causationId")).isEqualTo(header(checkedOut, "messageId"));
        assertThat(header(placeOrder, "correlationId")).isEqualTo(header(checkedOut, "correlationId"));
        assertThat(traceIdOf(placeOrder)).isEqualTo(traceIdOf(checkedOut));
    }

    @Test
    public void forwarderPublishSpanIsInTheStoredEventsTrace() {
        var order = checkedOutOrder();

        var stored = lastOf(cartStreamOf(order));
        var published =
                awaitRecord(ShoppingCartsConfig.external, order.orderId(), "shopping_carts.ShoppingCartCheckedOut");

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
    public void purchaseOnKafkaFormsTwoTracesSplitAtPayNow() {
        var order = paidOrder();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> given(() -> order.orderId())
                        .when(GET("/%s/orders".formatted(order.customerId()), JsonNode.class))
                        .then(response -> assertThat(((JsonNode) response.getResponseBody())
                                        .get("progress")
                                        .asString())
                                .isEqualTo("Completed")));

        var streamIds = List.of(orderStreamOf(order), paymentStreamOf(order), shipmentStreamOf(order));
        var events = streamIds.stream()
                .flatMap(stream -> metadataOf(stream).stream())
                .toList();
        var firstTrace = metadataOf(orderStreamOf(order)).getFirst();
        var payNow = eventsOf(paymentStreamOf(order)).stream()
                .filter(event -> event.data() instanceof CheckoutSessionCreated)
                .findFirst()
                .orElseThrow()
                .metadata();

        assertThat(firstTrace.correlationId()).isNotEqualTo(payNow.correlationId());
        assertThat(events)
                .allSatisfy(event ->
                        assertThat(event.correlationId()).isIn(firstTrace.correlationId(), payNow.correlationId()));
        assertThat(eventsWith(events, firstTrace.correlationId()).map(EventMetadata::traceId))
                .containsOnly(firstTrace.traceId());
        assertThat(eventsWith(events, payNow.correlationId()).map(EventMetadata::traceId))
                .containsOnly(payNow.traceId());
        assertThat(firstTrace.traceId()).isNotEqualTo(payNow.traceId());
        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(stepsIn(firstTrace.traceId())).isNotEmpty();
            assertThat(stepsIn(payNow.traceId())).isNotEmpty();
        });

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

    private PurchaseRestBuilder.BuilderResult paidOrder() {
        var builder = PurchaseRestBuilder.of(restTestClient, port);
        var order = builder.build(purchase -> purchase.withCustomerId(UUID.randomUUID())
                .withProduct(UUID.randomUUID(), 2)
                .checkedOut());
        var checkoutPage = builder.openCheckout(order.orderId(), order.paymentId());
        builder.payOn(checkoutPage, "4242 4242 4242 4242");

        return order;
    }

    private static String cartStreamOf(PurchaseRestBuilder.BuilderResult order) {
        return ShoppingCartFacade.mapToStreamId(ShoppingCartId.of(order.customerId(), 1));
    }

    private static String orderStreamOf(PurchaseRestBuilder.BuilderResult order) {
        return OrderFacade.mapToStreamId(new OrderId(Urns.of("ecommerce", "order", order.orderId())));
    }

    private static String paymentStreamOf(PurchaseRestBuilder.BuilderResult order) {
        return PaymentFacade.mapToStreamId(new PaymentId(Urns.of("ecommerce", "payment", order.orderId())));
    }

    private static String shipmentStreamOf(PurchaseRestBuilder.BuilderResult order) {
        return ShipmentFacade.mapToStreamId(shipmentIdOf(order));
    }

    private static ShipmentId shipmentIdOf(PurchaseRestBuilder.BuilderResult order) {
        return new ShipmentId(Urns.of("ecommerce", "shipment", order.orderId()));
    }

    private List<EventEnvelope<Object>> eventsOf(String streamId) {
        return eventStore.read(streamId) instanceof EventStore.ReadResult.Success success
                ? success.events()
                : List.of();
    }

    private List<EventMetadata> metadataOf(String streamId) {
        return eventsOf(streamId).stream().map(EventEnvelope::metadata).toList();
    }

    private EventMetadata lastOf(String streamId) {
        return metadataOf(streamId).getLast();
    }

    private static Stream<EventMetadata> eventsWith(List<EventMetadata> events, String correlationId) {
        return events.stream().filter(event -> event.correlationId().equals(correlationId));
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

    private List<SpanData> stepsIn(String traceId) {
        return spans.getFinishedSpanItems().stream()
                .filter(span -> span.getName().startsWith("handle ")
                        && span.getTraceId().equals(traceId))
                .toList();
    }

    private ConsumerRecord<String, byte[]> awaitRecord(String topic, String text, String messageType) {
        await().atMost(TIMEOUT).until(() -> recordOf(topic, text, messageType) != null);

        return recordOf(topic, text, messageType);
    }

    private ConsumerRecord<String, byte[]> recordOf(String topic, String text, String messageType) {
        return KafkaTopics.recordsOf(KAFKA, topic).stream()
                .filter(record ->
                        new String(record.value()).contains(text) && messageType.equals(header(record, "messageType")))
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
