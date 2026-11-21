package io.eventdriven.distributedprocesses.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.testing.RabbitMQManagement;
import io.eventdriven.testing.RabbitMQQueues;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestMessages;
import io.eventdriven.testing.TestRabbitMQ;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest
@ActiveProfiles("in-memory")
public class RabbitMQMessageBusDeadLetterOutageTests {
    private static final RabbitMQContainer rabbitMQ = TestDatabases.newRabbitMQContainer()
            .withEnv(
                    "RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS", "-rabbit dead_letter_worker_publisher_confirm_timeout 5000");

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
    public void deadLetterStaysInTheSourceQueueWhileTheDeadLetterQueueCantTakeIt() {
        var connection = TestDatabases.connectionTo(rabbitMQ);
        var management = new RabbitMQManagement(connection);
        var orders = "orders";
        var deadLetterQueue = orders + "-dlq";
        var attempts = new AtomicInteger();
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
                .subscribe(orders, MessageBusTests.OrderPlaced.class, event -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("Order cannot be handled");
                });

        try (var queues = new RabbitMQQueues(connection)) {
            management.setPolicy(
                    "dead-letter-queue-is-full",
                    "^" + deadLetterQueue + "$",
                    Map.of("max-length", 0, "overflow", "reject-publish"));
            bus.start();

            bus.publish(
                    List.of(new MessageBusTests.OrderPlaced("order-1"), new MessageBusTests.OrderPlaced("order-2")));

            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(() -> assertThat(attempts).hasValue(6));
            await().during(Duration.ofSeconds(7)).atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                assertThat(management.messagesIn(orders)).isEqualTo(1);
                assertThat(queues.messageCount(deadLetterQueue)).isEqualTo(1);
            });

            management.deletePolicy("dead-letter-queue-is-full");

            await().atMost(Duration.ofSeconds(60)).until(() -> queues.messageCount(deadLetterQueue) == 2);
            assertThat(queues.receiveAll(deadLetterQueue, 2)).hasSize(2);
            await().atMost(Duration.ofSeconds(30))
                    .untilAsserted(
                            () -> assertThat(management.messagesIn(orders)).isZero());
        } finally {
            bus.close();
        }
    }
}
