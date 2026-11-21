package io.eventdriven.distributedprocesses.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.eventdriven.testing.RabbitMQConnection;
import io.eventdriven.testing.RabbitMQManagement;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestMessages;
import io.eventdriven.testing.TestRabbitMQ;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
public class RabbitMQMessageBusConnectionLossTests {
    private static final RabbitMQContainer rabbitMQ = TestDatabases.newRabbitMQContainer();

    private final List<String> log = new CopyOnWriteArrayList<>();

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
    public void bindingsExistAgainAfterTheConnectionComesBack() {
        var connection = TestDatabases.connectionTo(rabbitMQ);
        var management = new RabbitMQManagement(connection);
        var orders = "orders";
        var bus = busOn(connection, orders);
        bus.start();
        var binding = new RabbitMQManagement.Binding(orders, orders, "#");
        assertThat(management.bindingsOf(orders)).containsExactly(binding);

        management.deleteBinding(orders, orders, "#");
        assertThat(management.bindingsOf(orders)).isEmpty();
        await().atMost(Duration.ofSeconds(30)).until(() -> management.connectionCount() > 0);
        management.closeAllConnections();

        await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(management.bindingsOf(orders)).containsExactly(binding));
        bus.publish(List.of(new MessageBusTests.OrderPlaced("order-1")));
        await().atMost(Duration.ofSeconds(60))
                .untilAsserted(() -> assertThat(log).containsExactly("order-1"));
        bus.close();
    }

    private RabbitMQMessageBus busOn(RabbitMQConnection connection, String orders) {
        return TestRabbitMQ.busOn(
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
    }
}
