package io.eventdriven.distributedprocesses.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import io.eventdriven.testing.InMemorySpansConfiguration;
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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
    protected MessageBus createBus() {
        return TestRabbitMQ.busOn(connection, messageContext, applicationContext, serializer());
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

        newBus().channel(lonely);

        assertThat(management.exchangeNames()).contains(lonely);
    }

    @Test
    public void publishToAChannelOfRequestsWithNoQueueFails() {
        var requests = "payments-" + id + "-requests";
        var bus = newBus().routes(new MessageRoutes().routeTarget(ConfirmOrder.class, command -> requests))
                .channel(requests);

        assertThatThrownBy(() -> publish(bus, new ConfirmOrder("order-1")))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining(requests);
    }

    @Test
    public void commandsToChannelsOfRequestsAndInternalMessagesWithAQueueAreHandled() {
        var requests = "payments-" + id + "-requests";
        var internal = "payments-" + id + "-internal";
        var bus = newBus().routes(new MessageRoutes()
                        .routeTarget(ConfirmOrder.class, command -> requests)
                        .routeTarget(OrderCanceled.class, event -> internal))
                .channel(requests)
                .channel(internal)
                .bind(requests, requests, "#")
                .bind(internal, internal, "#")
                .subscribe(requests, ConfirmOrder.class, command -> log.add("confirm"))
                .subscribe(internal, OrderCanceled.class, event -> log.add("canceled"));

        publish(bus, new ConfirmOrder("order-1"), new OrderCanceled("order-1"));

        awaitLog("confirm", "canceled");
    }

    @Test
    public void publishToAChannelOfResponsesWithNoQueueSucceeds() {
        var responses = "payments-" + id + "-responses";
        var bus = newBus().routes(new MessageRoutes().routeTarget(OrderPlaced.class, event -> responses))
                .channel(responses);

        publish(bus, new OrderPlaced("order-1"), new OrderPlaced("order-2"));
    }

    @Test
    public void publishToAnExchangeDeletedOnTheBrokerFailsWithinThePublishTimeout() {
        var deleted = "deleted-" + id;
        var bus = newBus().routes(new MessageRoutes().routeTarget(OrderPlaced.class, event -> deleted))
                .channel(deleted);
        publish(bus, new OrderPlaced("order-1"));
        management.deleteContaining(deleted);
        var startedAt = System.nanoTime();

        assertThatThrownBy(() -> publish(bus, new OrderPlaced("order-2"))).isInstanceOf(AmqpException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(PUBLISH_TIMEOUT);
    }

    @Test
    public void consumerMapsTheStableTypeNameToTheCurrentClass() {
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("placed:" + event.id()));
        var properties = MessagePropertiesBuilder.newInstance()
                .setHeader(RabbitMQMessageBus.MESSAGE_TYPE, "test.OrderPlaced")
                .build();

        queues.send(orders, KEY, new Message("{\"id\":\"order-1\"}".getBytes(StandardCharsets.UTF_8), properties));
        start((RabbitMQMessageBus) messageBus);

        awaitLog("placed:order-1");
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
        await().atMost(Duration.ofSeconds(30)).until(() -> seen.size() == 4);
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
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("placed:" + event.id()));
        queues.send(orders, KEY, new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties()));

        publish(messageBus, new OrderPlaced("order-1"));

        awaitLog("placed:order-1");
    }

    @Test
    public void consumerOpensAStepSpanNamedAfterTheMessageType() {
        messageBus.subscribe(orders, OrderPlaced.class, event -> log.add("placed"));

        publish(messageBus, new OrderPlaced("order-1"));

        awaitLog("placed");
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(spans.getFinishedSpanItems())
                        .extracting(span -> span.getName())
                        .contains("handle OrderPlaced"));
    }

    private void start(RabbitMQMessageBus bus) {
        if (!bus.isRunning()) bus.start();
    }

    private static MessageSerializer serializer() {
        return TestMessages.serializer(messageMapping());
    }
}
