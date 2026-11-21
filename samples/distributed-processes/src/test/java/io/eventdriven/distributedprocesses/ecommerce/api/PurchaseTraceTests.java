package io.eventdriven.distributedprocesses.ecommerce.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.eventstore.EventStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
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
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

public abstract class PurchaseTraceTests extends ApiSpecification {
    protected static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    protected EventStore eventStore;

    @Autowired
    protected InMemorySpanExporter spans;

    protected PurchaseTraceTests() {
        super("customers");
    }

    @Test
    public void aPurchaseFormsTwoTracesSplitAtPayNow() {
        var order = paidOrder();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> given(() -> order.orderId())
                        .when(GET("/%s/orders".formatted(order.customerId()), JsonNode.class))
                        .then(response -> assertThat(((JsonNode) response.getResponseBody())
                                        .get("progress")
                                        .asString())
                                .isEqualTo("Completed")));

        var events = eventsOfPurchase(order);
        var firstTrace = metadataOf(orderStreamOf(order)).getFirst();
        var payNow = payNowOf(order);

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

    protected PurchaseRestBuilder.BuilderResult paidOrder() {
        return PurchaseRestBuilder.of(restTestClient, port)
                .build(purchase -> purchase.withCustomerId(UUID.randomUUID())
                        .withProduct(UUID.randomUUID(), 2)
                        .paid());
    }

    protected static String orderStreamOf(PurchaseRestBuilder.BuilderResult order) {
        return OrderFacade.mapToStreamId(new OrderId(Urns.of("ecommerce", "order", order.orderId())));
    }

    protected static String paymentStreamOf(PurchaseRestBuilder.BuilderResult order) {
        return PaymentFacade.mapToStreamId(new PaymentId(Urns.of("ecommerce", "payment", order.orderId())));
    }

    protected static String shipmentStreamOf(PurchaseRestBuilder.BuilderResult order) {
        return ShipmentFacade.mapToStreamId(shipmentIdOf(order));
    }

    protected static ShipmentId shipmentIdOf(PurchaseRestBuilder.BuilderResult order) {
        return new ShipmentId(Urns.of("ecommerce", "shipment", order.orderId()));
    }

    protected List<EventMetadata> eventsOfPurchase(PurchaseRestBuilder.BuilderResult order) {
        return Stream.of(orderStreamOf(order), paymentStreamOf(order), shipmentStreamOf(order))
                .flatMap(stream -> metadataOf(stream).stream())
                .toList();
    }

    protected EventMetadata payNowOf(PurchaseRestBuilder.BuilderResult order) {
        return eventsOf(paymentStreamOf(order)).stream()
                .filter(event -> event.data() instanceof CheckoutSessionCreated)
                .findFirst()
                .orElseThrow()
                .metadata();
    }

    protected List<EventEnvelope<Object>> eventsOf(String streamId) {
        return eventStore.read(streamId) instanceof EventStore.ReadResult.Success success
                ? success.events()
                : List.of();
    }

    protected List<EventMetadata> metadataOf(String streamId) {
        return eventsOf(streamId).stream().map(EventEnvelope::metadata).toList();
    }

    protected static Stream<EventMetadata> eventsWith(List<EventMetadata> events, String correlationId) {
        return events.stream().filter(event -> event.correlationId().equals(correlationId));
    }

    protected List<SpanData> stepsIn(String traceId) {
        return spans.getFinishedSpanItems().stream()
                .filter(span -> span.getName().startsWith("handle ")
                        && span.getTraceId().equals(traceId))
                .toList();
    }
}
