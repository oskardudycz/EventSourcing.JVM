package io.eventdriven.core.messaging.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.eventdriven.core.messaging.BrokerMessageBusTests;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.messaging.rabbitmq.RabbitMQConnection;
import io.eventdriven.testing.messaging.rabbitmq.RabbitMQManagement;
import io.eventdriven.testing.messaging.rabbitmq.RabbitMQQueues;
import io.eventdriven.testing.messaging.rabbitmq.TestRabbitMQ;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

public class RabbitMQMessageBusTests extends BrokerMessageBusTests {

    private final RabbitMQConnection connection = TestDatabases.rabbitMQ();
    private final RabbitMQManagement management = new RabbitMQManagement(connection);
    private RabbitMQQueues queues;

    @Autowired
    private ApplicationContext applicationContext;

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
    protected Supplier<PublishedMessage> collectFirstPublishedTo(String channel) {
        var collector = queues.collectEverythingPublishedTo(channel);

        return () -> {
            var properties = queues.receiveAll(collector, 1).getFirst().getMessageProperties();

            return new PublishedMessage(
                    properties.<String>getHeader(RabbitMQMessageBus.TYPE_ID),
                    properties.getMessageId(),
                    properties.<String>getHeader("correlationId"),
                    properties.<String>getHeader("causationId"),
                    properties.<String>getHeader("traceparent"));
        };
    }

    @Override
    protected void sendRaw(String typeId, byte[] body) {
        var properties = MessagePropertiesBuilder.newInstance().setContentType(MessageProperties.CONTENT_TYPE_JSON);
        if (typeId != null) properties.setHeader(RabbitMQMessageBus.TYPE_ID, typeId);

        queues.send(orders, KEY, new Message(body, properties.build()));
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

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(TestRabbitMQ.PUBLISH_TIMEOUT);
    }

    @Test
    public void publishedMessageIsPersistentAndWrittenUnderItsStableTypeName() {
        var collector = queues.collectEverythingPublishedTo(orders);

        publish(messageBus, new OrderPlaced("order-1"));

        var properties = queues.receiveAll(collector, 1).getFirst().getMessageProperties();
        assertThat(properties.<String>getHeader(RabbitMQMessageBus.TYPE_ID)).isEqualTo("test.OrderPlaced");
        assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
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
}
