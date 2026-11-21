package io.eventdriven.distributedprocesses.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.testing.RabbitMQManagement;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestMessages;
import io.eventdriven.testing.TestRabbitMQ;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest
@ActiveProfiles("in-memory")
public class RabbitMQMessageBusBlockedPublisherTests {
    private static final RabbitMQContainer rabbitMQ = TestDatabases.newRabbitMQContainer();

    private final List<String> log = new CopyOnWriteArrayList<>();
    private final AtomicReference<AmqpException> publishFailure = new AtomicReference<>();

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeAll
    static void startRabbitMQ() {
        rabbitMQ.start();
    }

    @AfterAll
    static void stopRabbitMQ() {
        rabbitMQ.stop();
    }

    @Test
    public void consumersKeepReceivingWhilePublishingIsBlocked() throws Exception {
        var connection = TestDatabases.connectionTo(rabbitMQ);
        var management = new RabbitMQManagement(connection);
        var orders = "orders";
        var bus = TestRabbitMQ.busOn(
                        connection,
                        messageContext,
                        applicationContext,
                        TestMessages.serializer(MessageBusTests.messageMapping()))
                .routes(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage(MessageBusTests.class.getPackageName()), orders)
                        .routeKey(MessageBusTests.OrderMessage.class, message -> "ecommerce.order.1"))
                .channel(orders)
                .bind(orders, orders, "#")
                .subscribe(orders, MessageBusTests.OrderPlaced.class, event -> log.add(event.id()));
        bus.publish(List.of(new MessageBusTests.OrderPlaced("order-1")));

        try {
            assertThat(rabbitMQ.execInContainer("rabbitmqctl", "set_vm_memory_high_watermark", "0")
                            .getExitCode())
                    .isZero();
            var blockedPublisher = Thread.ofVirtual().start(() -> publishWhileBlocked(bus));
            awaitPublishingBlocked(management);

            bus.start();

            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(log).containsExactly("order-1"));
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(
                            () -> assertThat(management.messagesIn(orders)).isZero());
            blockedPublisher.interrupt();
        } finally {
            assertThat(rabbitMQ.execInContainer("rabbitmqctl", "set_vm_memory_high_watermark", "0.4")
                            .getExitCode())
                    .isZero();
            bus.close();
        }
    }

    private void awaitPublishingBlocked(RabbitMQManagement management) {
        try {
            await().atMost(Duration.ofSeconds(30)).until(() -> isPublishingBlocked(management));
        } catch (ConditionTimeoutException e) {
            var failure = publishFailure.get();
            if (failure != null) throw new AssertionError("The publish failed before it was blocked", failure);
            throw e;
        }
    }

    private void publishWhileBlocked(RabbitMQMessageBus bus) {
        try {
            bus.publish(List.of(new MessageBusTests.OrderPlaced("order-2")));
        } catch (AmqpException publishFailedBecauseItWasBlocked) {
            publishFailure.set(publishFailedBecauseItWasBlocked);
        }
    }

    private static boolean isPublishingBlocked(RabbitMQManagement management) {
        return management.connectionStates().stream().anyMatch(state -> state.startsWith("block"));
    }
}
