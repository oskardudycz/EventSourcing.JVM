package io.eventdriven.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.testing.tracing.InMemorySpansConfiguration;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
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
public abstract class BrokerMessageBusTests extends MessageBusTests {
    protected static final String KEY = "ecommerce.order.1";

    public record PublishedMessage(
            String typeId, String messageId, String correlationId, String causationId, String traceparent) {}

    @Autowired
    protected MessageContext messageContext;

    @Autowired
    protected Tracer tracer;

    @Autowired
    protected InMemorySpanExporter spans;

    @Override
    protected List<SpanData> finishedSpans() {
        return spans.getFinishedSpanItems();
    }

    protected abstract Supplier<List<PublishedMessage>> collectPublishedTo(String channel, int count);

    protected Supplier<PublishedMessage> collectFirstPublishedTo(String channel) {
        var published = collectPublishedTo(channel, 1);

        return () -> published.get().getFirst();
    }

    protected abstract void sendRaw(String typeId, byte[] body);

    @Test
    public void consumerMapsTheTypeIdToTheClassItNames() {
        messageBus.subscribe(orders, OrderPlaced.class, messages::catchMessage);

        sendRaw("test.OrderPlaced", "{\"id\":\"order-1\"}".getBytes(StandardCharsets.UTF_8));

        messageBus.whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
    }

    @Test
    public void aPublishedMessageCarriesItsClassNameAsTypeId() {
        var referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
        var authorized = new PaymentEvent.PaymentAuthorized(referenceId, OffsetDateTime.now(ZoneOffset.UTC));
        var bus = newBus(
                List.of(
                        ordersRoutes(),
                        new MessageRoutes()
                                .route(
                                        PaymentEvent.class,
                                        event -> new RoutingTarget(orders, Urns.routingKeyOf(event.referenceId())))),
                List.of(new MessageChannel(orders)),
                List.of(ordersQueue()));
        var published = collectFirstPublishedTo(orders);

        bus.publish(List.of(authorized));

        assertThat(published.get().typeId())
                .isEqualTo("io.eventdriven.core.messaging.MessageBusTests$PaymentEvent$PaymentAuthorized");
    }

    @Test
    public void aQueueBoundToAChannelDeclaredByALaterModuleGetsItsMessages() {
        var responses = "payments-responses-" + id;
        var responsesForOrders = "orders-payments-responses-" + id;
        var referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
        var authorized = new PaymentEvent.PaymentAuthorized(referenceId, OffsetDateTime.now(ZoneOffset.UTC));
        var bus = newBus(
                List.of(new MessageRoutes()
                        .route(
                                PaymentEvent.class,
                                event -> new RoutingTarget(responses, Urns.routingKeyOf(event.referenceId())))),
                List.of(new MessageChannel(responses)),
                List.of(new MessageQueue(responsesForOrders, responses, "ecommerce.order.*")));
        bus.subscribe(responsesForOrders, PaymentEvent.PaymentAuthorized.class, messages::catchMessage);

        bus.publish(List.of(authorized));

        bus.whenCaughtUp();
        assertThat(messages.published).contains(authorized);
    }

    @Test
    public void anExplicitNameTravelsAndIsReadBack() {
        messageBus.subscribe(orders, OrderPlaced.class, messages::catchMessage);
        var published = collectFirstPublishedTo(orders);

        messageBus.publish(List.of(new OrderPlaced("order-1")));

        assertThat(published.get().typeId()).isEqualTo("test.OrderPlaced");
        messageBus.whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
    }

    @Test
    public void anOffsetDateTimeKeepsItsOffset() {
        var ownOrders = "own-orders-" + id;
        var referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
        var authorizedAt = OffsetDateTime.of(2024, 5, 1, 10, 15, 30, 0, ZoneOffset.ofHours(2));
        var authorized = new PaymentEvent.PaymentAuthorized(referenceId, authorizedAt);
        var bus = newBus(
                List.of(new MessageRoutes()
                        .route(
                                PaymentEvent.class,
                                event -> new RoutingTarget(orders, Urns.routingKeyOf(event.referenceId())))),
                List.of(new MessageChannel(orders)),
                List.of(new MessageQueue(ownOrders, orders, "#")));
        bus.subscribe(ownOrders, PaymentEvent.PaymentAuthorized.class, messages::catchMessage);

        bus.publish(List.of(authorized));

        bus.whenCaughtUp();
        assertThat(messages.published).hasSize(1);
        assertThat(((PaymentEvent.PaymentAuthorized) messages.published.getFirst()).authorizedAt())
                .isEqualTo(authorizedAt)
                .satisfies(received -> assertThat(received.getOffset()).isEqualTo(ZoneOffset.ofHours(2)));
    }

    @Test
    public void messageStartingAProcessCarriesAMessageIdAndAGeneratedCorrelationId() {
        var published = collectFirstPublishedTo(orders);

        messageBus.publish(List.of(new OrderPlaced("order-1")));

        var message = published.get();
        assertThat(UUID.fromString(message.messageId())).isNotNull();
        assertThat(UUID.fromString(message.correlationId())).isNotNull();
        assertThat(message.causationId()).isEqualTo(message.correlationId());
        assertThat(message.traceparent()).isNotBlank();
    }

    @Test
    public void messagesPublishedAgainWhileHandlingARedeliveredMessageKeepTheirIds() {
        var published = collectPublishedTo(orders, 4);

        for (var delivery = 0; delivery < 2; delivery++)
            try (var _ = messageContext.handling("redelivered-message-id", "process-correlation-id")) {
                messageBus.publish(List.of(new ConfirmOrder("order-1"), new OrderCanceled("order-1")));
            }

        var messageIds =
                published.get().stream().map(PublishedMessage::messageId).toList();
        assertThat(messageIds).hasSize(4).doesNotContain("redelivered-message-id");
        assertThat(messageIds.get(0)).isEqualTo(messageIds.get(2)).isNotEqualTo(messageIds.get(1));
        assertThat(messageIds.get(1)).isEqualTo(messageIds.get(3));
    }

    @Test
    public void messagePublishedWhileHandlingAnotherCarriesItsCorrelationAndCausationIds() {
        var published = collectFirstPublishedTo(orders);

        try (var _ = messageContext.handling("handled-message-id", "process-correlation-id")) {
            messageBus.publish(List.of(new OrderPlaced("order-1")));
        }

        var message = published.get();
        assertThat(message.correlationId()).isEqualTo("process-correlation-id");
        assertThat(message.causationId()).isEqualTo("handled-message-id");
        assertThat(message.messageId()).isNotEqualTo("handled-message-id");
    }

    @Test
    public void handlerRunsInTheTraceOfThePublishedMessageWithAllItsValues() {
        var published = collectFirstPublishedTo(orders);
        var seen = new CopyOnWriteArrayList<String>();
        messageBus.subscribe(orders, OrderPlaced.class, event -> {
            var context = tracer.currentSpan().context();
            seen.add(tracer.getBaggage("correlationId").get());
            seen.add(tracer.getBaggage("causationId").get());
            seen.add(context.traceId());
            seen.add(context.spanId());
        });
        var publisherSpan = tracer.nextSpan().name("publisher").start();

        try (var _ = tracer.withSpan(publisherSpan);
                var _ = messageContext.handling("handled-message-id", "process-correlation-id")) {
            messageBus.publish(List.of(new OrderPlaced("order-1")));
        } finally {
            publisherSpan.end();
        }

        var messageId = published.get().messageId();
        messageBus.whenCaughtUp();
        assertThat(seen).hasSize(4);
        assertThat(seen.subList(0, 3))
                .containsExactly(
                        "process-correlation-id",
                        messageId,
                        publisherSpan.context().traceId());
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems()).anySatisfy(span -> {
                    assertThat(span.getName()).isEqualTo("handle OrderPlaced");
                    assertThat(span.getSpanContext().getSpanId()).isEqualTo(seen.get(3));
                    assertThat(span.getSpanContext().getTraceId()).isEqualTo(seen.get(2));
                }));
    }

    @Test
    public void aMessageWithoutATypeIdDoesNotStopTheNextMessageFromBeingHandled() {
        messageBus.subscribe(orders, OrderPlaced.class, messages::catchMessage);
        sendRaw(null, "{}".getBytes(StandardCharsets.UTF_8));

        messageBus.publish(List.of(new OrderPlaced("order-1")));

        messageBus.whenCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
    }
}
