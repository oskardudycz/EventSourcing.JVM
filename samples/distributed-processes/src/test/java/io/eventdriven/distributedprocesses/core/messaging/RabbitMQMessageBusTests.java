package io.eventdriven.distributedprocesses.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrdersConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentsConfig;
import io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.testing.InMemorySpansConfiguration;
import io.eventdriven.testing.MessageCatcher;
import io.eventdriven.testing.RabbitMQConnection;
import io.eventdriven.testing.RabbitMQManagement;
import io.eventdriven.testing.RabbitMQQueues;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestMessages;
import io.eventdriven.testing.TestRabbitMQ;
import io.micrometer.tracing.Tracer;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
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
public class RabbitMQMessageBusTests extends MessageBusTests {
    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(10);
    private static final String KEY = "ecommerce.order.1";

    private final RabbitMQConnection connection = TestDatabases.rabbitMQ();
    private final RabbitMQManagement management = new RabbitMQManagement(connection);
    private RabbitMQQueues queues;

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Tracer tracer;

    @Autowired
    private InMemorySpanExporter spans;

    @BeforeEach
    void connectQueues() {
        queues = new RabbitMQQueues(connection);
    }

    @AfterEach
    void closeQueues() {
        queues.close();
    }

    @Override
    protected MessageBus createBus(
            List<MessageRoutes> routes, List<MessageChannel> channels, List<MessageQueue> queues) {
        return TestRabbitMQ.busOn(
                connection, messageContext, applicationContext, serializer(), routes, channels, queues);
    }

    @Override
    protected void beforePublish(MessageBus bus) {
        start((RabbitMQMessageBus) bus);
    }

    @Override
    protected void dispose(MessageBus bus) {
        ((RabbitMQMessageBus) bus).close();
        management.deleteContaining(id);
    }

    @Test
    public void everyDeclaredChannelIsADurableTopicExchangeAndEveryQueueIsADurableQuorumQueue() {
        assertThat(management.exchangeNames()).contains(orders);
        assertThat(management.queueNames()).contains(orders);
        assertThat(management.bindingsOf(orders)).containsExactly(new RabbitMQManagement.Binding(orders, orders, "#"));
        assertThat(management.exchange(orders)).containsEntry("type", "topic").containsEntry("durable", true);
        assertThat(management.queue(orders)).containsEntry("type", "quorum").containsEntry("durable", true);
    }

    @Test
    public void aChannelWithNoQueueIsStillAnExchange() {
        var lonely = "lonely-" + id;

        newBus(List.of(), List.of(new MessageChannel(lonely)), List.of());

        assertThat(management.exchangeNames()).contains(lonely);
    }

    @Test
    public void publishToAChannelOfRequestsWithNoQueueFails() {
        var requests = "payments-" + id + "-requests";
        var bus = newBus(
                List.of(new MessageRoutes().routeTarget(ConfirmOrder.class, command -> requests)),
                List.of(new MessageChannel(requests)),
                List.of());

        assertThatThrownBy(() -> publish(bus, new ConfirmOrder("order-1")))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining(requests);
    }

    @Test
    public void commandsToChannelsOfRequestsAndInternalMessagesWithAQueueAreHandled() {
        var requests = "payments-" + id + "-requests";
        var internal = "payments-" + id + "-internal";
        var canceled = new MessageCatcher();
        var bus = newBus(
                List.of(new MessageRoutes()
                        .routeTarget(ConfirmOrder.class, command -> requests)
                        .routeTarget(OrderCanceled.class, event -> internal)),
                List.of(new MessageChannel(requests), new MessageChannel(internal)),
                List.of(new MessageQueue(requests, requests, "#"), new MessageQueue(internal, internal, "#")));
        bus.subscribe(requests, ConfirmOrder.class, messages::catchMessage);
        bus.subscribe(internal, OrderCanceled.class, canceled::catchMessage);

        publish(bus, new ConfirmOrder("order-1"), new OrderCanceled("order-1"));

        awaitCaughtUp();
        messages.shouldReceiveSingleEvent(new ConfirmOrder("order-1"));
        canceled.shouldReceiveSingleEvent(new OrderCanceled("order-1"));
    }

    @Test
    public void publishToAChannelOfResponsesWithNoQueueSucceeds() {
        var responses = "payments-" + id + "-responses";
        var bus = newBus(
                List.of(new MessageRoutes().routeTarget(OrderPlaced.class, event -> responses)),
                List.of(new MessageChannel(responses)),
                List.of());

        publish(bus, new OrderPlaced("order-1"), new OrderPlaced("order-2"));
    }

    @Test
    public void publishToAnExchangeDeletedOnTheBrokerFailsWithinThePublishTimeout() {
        var deleted = "deleted-" + id;
        var bus = newBus(
                List.of(new MessageRoutes().routeTarget(OrderPlaced.class, event -> deleted)),
                List.of(new MessageChannel(deleted)),
                List.of());
        publish(bus, new OrderPlaced("order-1"));
        management.deleteContaining(deleted);
        var startedAt = System.nanoTime();

        assertThatThrownBy(() -> publish(bus, new OrderPlaced("order-2"))).isInstanceOf(AmqpException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(PUBLISH_TIMEOUT);
    }

    @Test
    public void consumerMapsTheStableTypeNameToTheCurrentClass() {
        messageBus.subscribe(orders, OrderPlaced.class, messages::catchMessage);
        var properties = MessagePropertiesBuilder.newInstance()
                .setHeader(RabbitMQMessageBus.MESSAGE_TYPE, "test.OrderPlaced")
                .build();

        queues.send(orders, KEY, new Message("{\"id\":\"order-1\"}".getBytes(StandardCharsets.UTF_8), properties));
        start((RabbitMQMessageBus) messageBus);

        awaitCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
    }

    @Test
    public void publishedMessageIsPersistentAndWrittenUnderItsStableTypeName() {
        var collector = queues.collectEverythingPublishedTo(orders);

        publish(messageBus, new OrderPlaced("order-1"));

        var properties = queues.receiveAll(collector, 1).getFirst().getMessageProperties();
        assertThat(properties.<String>getHeader(RabbitMQMessageBus.MESSAGE_TYPE))
                .isEqualTo("test.OrderPlaced");
        assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    }

    @Test
    public void messageStartingAProcessCarriesAMessageIdAndAGeneratedCorrelationId() {
        var collector = queues.collectEverythingPublishedTo(orders);

        publish(messageBus, new OrderPlaced("order-1"));

        var properties = queues.receiveAll(collector, 1).getFirst().getMessageProperties();
        var correlationId = properties.<String>getHeader("correlationId");
        assertThat(UUID.fromString(properties.getMessageId())).isNotNull();
        assertThat(UUID.fromString(correlationId)).isNotNull();
        assertThat(properties.<String>getHeader("causationId")).isEqualTo(correlationId);
        assertThat(properties.<String>getHeader("traceparent")).isNotBlank();
    }

    @Test
    public void messagePublishedWhileHandlingAnotherCarriesItsCorrelationAndCausationIds() {
        var collector = queues.collectEverythingPublishedTo(orders);

        try (var _ = messageContext.handling("handled-message-id", "process-correlation-id")) {
            publish(messageBus, new OrderPlaced("order-1"));
        }

        var properties = queues.receiveAll(collector, 1).getFirst().getMessageProperties();
        assertThat(properties.<String>getHeader("correlationId")).isEqualTo("process-correlation-id");
        assertThat(properties.<String>getHeader("causationId")).isEqualTo("handled-message-id");
        assertThat(properties.getMessageId()).isNotEqualTo("handled-message-id");
    }

    @Test
    public void handlerRunsInTheTraceOfThePublishedMessageWithAllItsValues() {
        var collector = queues.collectEverythingPublishedTo(orders);
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
            publish(messageBus, new OrderPlaced("order-1"));
        } finally {
            publisherSpan.end();
        }

        var messageId = queues.receiveAll(collector, 1)
                .getFirst()
                .getMessageProperties()
                .getMessageId();
        awaitCaughtUp();
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
    public void aMessageWithoutATypeHeaderDoesNotStopTheNextMessageFromBeingHandled() {
        messageBus.subscribe(orders, OrderPlaced.class, messages::catchMessage);
        queues.send(orders, KEY, new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties()));

        publish(messageBus, new OrderPlaced("order-1"));

        awaitCaughtUp();
        messages.shouldReceiveSingleEvent(new OrderPlaced("order-1"));
    }

    @Test
    public void consumerOpensAStepSpanNamedAfterTheMessageType() {
        messageBus.subscribe(orders, OrderPlaced.class, messages::catchMessage);

        publish(messageBus, new OrderPlaced("order-1"));

        awaitCaughtUp();
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems())
                        .extracting(span -> span.getName())
                        .contains("handle OrderPlaced"));
    }

    @Test
    public void aQueueBoundToAChannelDeclaredByALaterModuleGetsItsMessages() {
        var referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
        var authorized = new PaymentExternalEvent.PaymentAuthorized(
                referenceId, PaymentId.derivedFrom(referenceId), OffsetDateTime.now(ZoneOffset.UTC));
        var bus = newBus(
                List.of(PaymentsConfig.paymentsRoutes()),
                List.of(
                        PaymentsConfig.paymentsRequestsChannel(),
                        PaymentsConfig.paymentsResponsesChannel(),
                        PaymentsConfig.paymentsInternalChannel()),
                List.of(OrdersConfig.ordersPaymentsResponsesQueue()));
        bus.subscribe(
                OrdersConfig.paymentsResponses, PaymentExternalEvent.PaymentAuthorized.class, messages::catchMessage);

        publish(bus, authorized);

        awaitCaughtUp();
        assertThat(messages.published).contains(authorized);
    }

    @Test
    public void waitingForCatchUpFailsNamingTheQueueWhenAHandlerDoesNotFinish() {
        var release = new CountDownLatch(1);
        messageBus.subscribe(orders, OrderPlaced.class, event -> {
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        publish(messageBus, new OrderPlaced("order-1"));

        try {
            assertThatThrownBy(() -> messageBus.awaitCaughtUp(Duration.ofSeconds(1)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(orders);
        } finally {
            release.countDown();
        }
    }

    private void start(RabbitMQMessageBus bus) {
        if (!bus.isRunning()) bus.start();
    }

    private static MessageSerializer serializer() {
        return TestMessages.serializer(messageMapping());
    }
}
