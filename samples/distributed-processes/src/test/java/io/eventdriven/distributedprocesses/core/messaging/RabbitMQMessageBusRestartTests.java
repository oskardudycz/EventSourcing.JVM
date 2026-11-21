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
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("in-memory")
public class RabbitMQMessageBusRestartTests {
    private final RabbitMQConnection connection = TestDatabases.rabbitMQ();
    private final RabbitMQManagement management = new RabbitMQManagement(connection);
    private final String id = UUID.randomUUID().toString().substring(0, 8);
    private final String orders = "orders-" + id;
    private final List<String> log = new CopyOnWriteArrayList<>();

    @Autowired
    private MessageContext messageContext;

    @Autowired
    private ApplicationContext applicationContext;

    @AfterEach
    void deleteTopology() {
        management.deleteContaining(id);
    }

    @Test
    public void waitingMessagesAreHandledAfterARestart() {
        var beforeRestart = newBus();
        beforeRestart.publish(
                List.of(new MessageBusTests.OrderPlaced("order-1"), new MessageBusTests.OrderPlaced("order-2")));
        beforeRestart.close();

        assertThat(management.exchangeNames()).contains(orders);
        assertThat(management.queueNames()).contains(orders);
        assertThat(management.bindingsOf(orders)).containsExactly(new RabbitMQManagement.Binding(orders, orders, "#"));

        var afterRestart = newBus();
        afterRestart.subscribe(orders, MessageBusTests.OrderPlaced.class, event -> log.add(event.id()));
        afterRestart.start();

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(log).containsExactlyInAnyOrder("order-1", "order-2"));
        afterRestart.close();
    }

    @Test
    public void closingRightAfterPublishingNeverFails() {
        for (var attempt = 0; attempt < 300; attempt++) {
            var bus = newBus();
            bus.publish(List.of(
                    new MessageBusTests.OrderPlaced("a-" + attempt), new MessageBusTests.OrderPlaced("b-" + attempt)));
            bus.close();
        }
    }

    private RabbitMQMessageBus newBus() {
        return TestRabbitMQ.busOn(
                        connection,
                        messageContext,
                        applicationContext,
                        TestMessages.serializer(MessageBusTests.messageMapping()))
                .routes(new MessageRoutes()
                        .routeTarget(MessageRoutes.inPackage(MessageBusTests.class.getPackageName()), orders)
                        .routeKey(MessageBusTests.OrderMessage.class, message -> "ecommerce.order.1"))
                .channel(orders)
                .bind(orders, orders, "#");
    }
}
