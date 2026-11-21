package io.eventdriven.distributedprocesses.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.awaitility.Awaitility.await;

import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageMapping;
import io.eventdriven.distributedprocesses.core.processing.RetryBackOff;
import io.eventdriven.testing.RabbitMQConnection;
import io.eventdriven.testing.RabbitMQManagement;
import io.eventdriven.testing.RabbitMQQueues;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestMessages;
import io.eventdriven.testing.TestRabbitMQ;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("in-memory")
public class RabbitMQMessageBusFailureTests {
    public record PaymentReceived(String id) {}

    public record RoomInspected(String room, String inspector) {}

    private static final String KEY = "ecommerce.payment.1";

    private final RabbitMQConnection connection = TestDatabases.rabbitMQ();
    private final RabbitMQManagement management = new RabbitMQManagement(connection);
    private final String id = UUID.randomUUID().toString().substring(0, 8);
    private final String payments = "payments-" + id;
    private final String billing = "billing-" + id;
    private final String shipping = "shipping-" + id;
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final List<String> log = new CopyOnWriteArrayList<>();
    private final AtomicInteger decisions = new AtomicInteger();

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ApplicationContext applicationContext;

    private RabbitMQQueues queues;
    private RabbitMQMessageBus messageBus;

    @BeforeEach
    void createMessageBus() {
        queues = new RabbitMQQueues(connection);
        messageBus = TestRabbitMQ.busOn(
                        connection,
                        messageContext,
                        applicationContext,
                        TestMessages.serializer(new MessageMapping()
                                .register("test.PaymentReceived", PaymentReceived.class)
                                .register("test.RoomInspected", RoomInspected.class)
                                .register(
                                        "test.RoomInspected.v1",
                                        String.class,
                                        room -> new RoomInspected(room, "unknown"))
                                .register(
                                        "test.RoomInspected.v0",
                                        json -> new RoomInspected(json.get("r").asString(), "unknown"))),
                        new RabbitMQMessageBus.FailureHandling(
                                (type, message, error) -> {
                                    decisions.incrementAndGet();
                                    return MessageFailureStrategy.retryUnlessUnreadable()
                                            .onFailure(type, message, error);
                                },
                                RetryBackOff.exponential(TestRabbitMQ.RETRY_INTERVAL),
                                meters))
                .routes(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage(getClass().getPackageName()), payments)
                        .routeKey(PaymentReceived.class, message -> KEY)
                        .routeKey(RoomInspected.class, message -> KEY))
                .channel(payments)
                .bind(billing, payments, "#");
    }

    @AfterEach
    void closeMessageBus() {
        messageBus.close();
        queues.close();
        management.deleteContaining(id);
    }

    @Test
    public void everyQueueGetsItsOwnDeadLetterQueueThroughADirectExchangeAndKeepsDeadLettersUntilTheyAreConfirmed() {
        assertThat(management.exchange(billing + "-dlx")).containsEntry("type", "direct");
        assertThat(management.bindingsOf(billing + "-dlq"))
                .containsExactly(new RabbitMQManagement.Binding(billing + "-dlx", billing + "-dlq", billing));
        assertThat(management.queue(billing + "-dlq"))
                .containsEntry("type", "quorum")
                .containsEntry("durable", true);
        assertThat(management.queue(billing))
                .extractingByKey("arguments")
                .asInstanceOf(MAP)
                .containsEntry("x-dead-letter-exchange", billing + "-dlx")
                .containsEntry("x-dead-letter-routing-key", billing)
                .containsEntry("x-dead-letter-strategy", "at-least-once")
                .containsEntry("x-overflow", "reject-publish");
    }

    @Test
    public void batchWithAMessageTypeWithoutAStableNamePublishesNothing() {
        record Unnamed(String id) {}
        var collector = queues.collectEverythingPublishedTo(payments);
        var bus = TestRabbitMQ.busOn(
                        connection,
                        messageContext,
                        applicationContext,
                        TestMessages.serializer(
                                new MessageMapping().register("test.PaymentReceived", PaymentReceived.class)))
                .routes(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage(getClass().getPackageName()), payments)
                        .routeKey(PaymentReceived.class, message -> KEY)
                        .routeKey(Unnamed.class, message -> KEY))
                .channel(payments);

        try {
            assertThatThrownBy(() -> bus.publish(List.of(new PaymentReceived("payment-1"), new Unnamed("unnamed"))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(Unnamed.class.getName());
        } finally {
            bus.close();
        }

        assertThat(queues.messageCount(collector)).isZero();
    }

    @Test
    public void handlerFailingOnceSucceedsOnTheSecondAttempt() {
        var attempts = new AtomicInteger();
        messageBus.subscribe(billing, PaymentReceived.class, message -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Payment gateway unavailable");
            log.add("handled:" + message.id());
        });

        publish(new PaymentReceived("payment-1"));

        awaitLog("handled:payment-1");
        assertThat(attempts).hasValue(2);
        assertThat(queues.messageCount(billing + "-dlq")).isZero();
        assertThat(deadLettered()).isZero();
    }

    @Test
    public void alwaysFailingHandlerSendsTheMessageToItsQueuesDeadLetterQueueAfterTheLastRetry() {
        var attempts = new AtomicInteger();
        messageBus.bind(shipping, payments, "#");
        messageBus.subscribe(billing, PaymentReceived.class, message -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("Payment gateway unavailable");
        });
        messageBus.subscribe(shipping, PaymentReceived.class, message -> log.add("shipping:" + message.id()));

        publish(new PaymentReceived("payment-1"), new PaymentReceived("payment-2"));

        var deadLetters = queues.receiveAll(billing + "-dlq", 2);
        awaitLog("shipping:payment-1", "shipping:payment-2");
        assertThat(deadLetters).hasSize(2);
        assertThat(attempts).hasValue(6);
        assertThat(queues.messageCount(shipping + "-dlq")).isZero();
        assertThat(deadLettered()).isEqualTo(2);
    }

    @Test
    public void bodyThatIsNotJsonGoesToTheDeadLetterQueueWithoutRetriesAndTheNextMessageIsHandled() {
        messageBus.subscribe(billing, PaymentReceived.class, message -> log.add("handled:" + message.id()));
        publishRaw("test.PaymentReceived", "{\"id\":");

        publish(new PaymentReceived("payment-1"));

        var deadLetters = queues.receiveAll(billing + "-dlq", 1);
        awaitLog("handled:payment-1");
        assertThat(deadLetters).hasSize(1);
        assertThat(new String(deadLetters.getFirst().getBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"id\":");
        assertThat(decisions).hasValue(1);
        assertThat(deadLettered()).isEqualTo(1);
    }

    @Test
    public void typeOutsideTheSamplesPackageIsNotCreatedAndIsDeadLettered() {
        messageBus.subscribe(billing, PaymentReceived.class, message -> log.add("handled:" + message.id()));
        publishRaw("java.lang.ProcessBuilder", "{}");

        publish(new PaymentReceived("payment-1"));

        var deadLetters = queues.receiveAll(billing + "-dlq", 1);
        awaitLog("handled:payment-1");
        assertThat(deadLetters).hasSize(1);
        assertThat(deadLetters.getFirst().getMessageProperties().<String>getHeader(RabbitMQMessageBus.MESSAGE_TYPE))
                .isEqualTo("java.lang.ProcessBuilder");
        assertThat(decisions).hasValue(1);
    }

    @Test
    public void typeNameInsideTheSamplesPackageThatNamesNoClassIsDeadLettered() {
        publishRaw("io.eventdriven.distributedprocesses.NoSuchMessage", "{}");

        assertThat(queues.receiveAll(billing + "-dlq", 1)).hasSize(1);
    }

    @Test
    public void messageWithoutATypeHeaderIsDeadLettered() {
        messageBus.subscribe(billing, PaymentReceived.class, message -> log.add("handled:" + message.id()));
        queues.send(payments, KEY, new Message("{}".getBytes(StandardCharsets.UTF_8)));

        publish(new PaymentReceived("payment-1"));

        assertThat(queues.receiveAll(billing + "-dlq", 1)).hasSize(1);
        awaitLog("handled:payment-1");
    }

    @Test
    public void deadLetterCountIsTaggedWithTheStableTypeNameAndTheQueue() {
        messageBus.subscribe(billing, PaymentReceived.class, message -> {
            throw new IllegalStateException("Payment gateway unavailable");
        });

        publish(new PaymentReceived("payment-1"));

        queues.receiveAll(billing + "-dlq", 1);
        assertThat(meters.find(RabbitMQMessageBus.DEAD_LETTERED)
                        .tag("message.type", "test.PaymentReceived")
                        .tag("messaging.destination.name", billing)
                        .counter())
                .isNotNull()
                .satisfies(counter -> assertThat(counter.count()).isEqualTo(1));
    }

    @Test
    public void deadLetterCountOfATypeThatIsNotRegisteredIsTaggedUnknown() {
        publishRaw("io.eventdriven.distributedprocesses.NoSuchMessage", "{}");
        publishRaw("java.lang.ProcessBuilder", "{}");

        queues.receiveAll(billing + "-dlq", 2);
        assertThat(meters.find(RabbitMQMessageBus.DEAD_LETTERED).counters())
                .extracting(counter -> counter.getId().getTag("message.type"))
                .containsExactly("unknown");
        assertThat(deadLettered()).isEqualTo(2);
    }

    @Test
    public void oldVersionOfAMessageIsTransformedToTheCurrentOne() {
        messageBus.subscribe(
                billing, RoomInspected.class, message -> log.add(message.room() + ":" + message.inspector()));
        publishRaw("test.RoomInspected.v1", "\"101\"");
        publishRaw("test.RoomInspected.v0", "{\"r\":\"102\"}");
        publishRaw("test.RoomInspected", "{\"room\":\"103\",\"inspector\":\"anna\"}");

        awaitLog("101:unknown", "102:unknown", "103:anna");
        assertThat(queues.messageCount(billing + "-dlq")).isZero();
    }

    private void publish(Object... messages) {
        if (!messageBus.isRunning()) messageBus.start();
        messageBus.publish(List.of(messages));
    }

    private void publishRaw(String typeName, String body) {
        var properties = MessagePropertiesBuilder.newInstance()
                .setHeader(RabbitMQMessageBus.MESSAGE_TYPE, typeName)
                .build();
        queues.send(payments, KEY, new Message(body.getBytes(StandardCharsets.UTF_8), properties));
        if (!messageBus.isRunning()) messageBus.start();
    }

    private double deadLettered() {
        return meters.find(RabbitMQMessageBus.DEAD_LETTERED).counters().stream()
                .mapToDouble(counter -> counter.count())
                .sum();
    }

    private void awaitLog(String... entries) {
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(log).containsExactlyInAnyOrder(entries));
    }
}
