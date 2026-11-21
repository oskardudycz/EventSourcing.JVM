package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.kurrentdb.EventStore;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import io.eventdriven.distributedprocesses.ecommerce.api.builders.PurchaseRestBuilder;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderFacade;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.CheckoutSessionCreated;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentFacade;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentFacade;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.testing.ApiSpecification;
import io.eventdriven.testing.InMemorySpansConfiguration;
import io.eventdriven.testing.TestDatabases;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

@DirtiesContext
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"messaging.bus=rabbitmq", "eventstore.type=postgresql", "projections.database=postgresql"})
@AutoConfigureTracing
@Import(InMemorySpansConfiguration.class)
public class PurchaseOnRabbitMQTraceTests extends ApiSpecification {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        var postgreSQL = TestDatabases.newPostgreSQLDatabase();
        var rabbitMQ = TestDatabases.rabbitMQ();

        registry.add("spring.datasource.url", postgreSQL::url);
        registry.add("spring.datasource.username", postgreSQL::username);
        registry.add("spring.datasource.password", postgreSQL::password);
        registry.add("eventstore.postgresql.url", postgreSQL::url);
        registry.add("eventstore.postgresql.username", postgreSQL::username);
        registry.add("eventstore.postgresql.password", postgreSQL::password);
        registry.add("spring.rabbitmq.host", rabbitMQ::host);
        registry.add("spring.rabbitmq.port", rabbitMQ::port);
        registry.add("spring.rabbitmq.username", rabbitMQ::username);
        registry.add("spring.rabbitmq.password", rabbitMQ::password);
    }

    @Autowired
    private EventStore eventStore;

    @Autowired
    private InMemorySpanExporter spans;

    public PurchaseOnRabbitMQTraceTests() {
        super("customers");
    }

    @Test
    public void purchaseOnRabbitMQAndPostgreSQLFormsTwoTracesSplitAtPayNow() {
        var builder = PurchaseRestBuilder.of(restTestClient, port);
        var order = builder.build(purchase -> purchase.withCustomerId(UUID.randomUUID())
                .withProduct(UUID.randomUUID(), 2)
                .checkedOut());
        var checkoutPage = builder.openCheckout(order.orderId(), order.paymentId());
        builder.payOn(checkoutPage, "4242 4242 4242 4242");

        await().atMost(TIMEOUT)
                .untilAsserted(() -> given(() -> order.orderId())
                        .when(GET("/%s/orders".formatted(order.customerId()), JsonNode.class))
                        .then(response -> assertThat(((JsonNode) response.getResponseBody())
                                        .get("progress")
                                        .asString())
                                .isEqualTo("Completed")));

        var events = Stream.of(orderStreamOf(order), paymentStreamOf(order), shipmentStreamOf(order))
                .flatMap(stream -> metadataOf(stream).stream())
                .toList();
        var firstTrace = metadataOf(orderStreamOf(order)).getFirst();
        var payNow = eventsOf(paymentStreamOf(order)).stream()
                .filter(event -> event.data() instanceof CheckoutSessionCreated)
                .findFirst()
                .orElseThrow()
                .metadata();

        assertThat(firstTrace.correlationId()).isNotEqualTo(payNow.correlationId());
        assertThat(events).allSatisfy(event -> {
            assertThat(event.correlationId()).isIn(firstTrace.correlationId(), payNow.correlationId());
            assertThat(event.causationId()).isNotBlank();
        });
        assertThat(eventsWith(events, firstTrace.correlationId()).map(EventMetadata::traceId))
                .containsOnly(firstTrace.traceId());
        assertThat(eventsWith(events, payNow.correlationId()).map(EventMetadata::traceId))
                .containsOnly(payNow.traceId());
        assertThat(firstTrace.traceId()).isNotEqualTo(payNow.traceId());
        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(stepsIn(firstTrace.traceId())).isNotEmpty();
            assertThat(stepsIn(payNow.traceId())).isNotEmpty();
        });
    }

    private static String orderStreamOf(PurchaseRestBuilder.BuilderResult order) {
        return OrderFacade.mapToStreamId(new OrderId(Urns.of("ecommerce", "order", order.orderId())));
    }

    private static String paymentStreamOf(PurchaseRestBuilder.BuilderResult order) {
        return PaymentFacade.mapToStreamId(new PaymentId(Urns.of("ecommerce", "payment", order.orderId())));
    }

    private static String shipmentStreamOf(PurchaseRestBuilder.BuilderResult order) {
        return ShipmentFacade.mapToStreamId(new ShipmentId(Urns.of("ecommerce", "shipment", order.orderId())));
    }

    private List<EventEnvelope<Object>> eventsOf(String streamId) {
        return eventStore.read(streamId) instanceof EventStore.ReadResult.Success success
                ? success.events()
                : List.of();
    }

    private List<EventMetadata> metadataOf(String streamId) {
        return eventsOf(streamId).stream().map(EventEnvelope::metadata).toList();
    }

    private static Stream<EventMetadata> eventsWith(List<EventMetadata> events, String correlationId) {
        return events.stream().filter(event -> event.correlationId().equals(correlationId));
    }

    private List<SpanData> stepsIn(String traceId) {
        return spans.getFinishedSpanItems().stream()
                .filter(span -> span.getName().startsWith("handle ")
                        && span.getTraceId().equals(traceId))
                .toList();
    }
}
