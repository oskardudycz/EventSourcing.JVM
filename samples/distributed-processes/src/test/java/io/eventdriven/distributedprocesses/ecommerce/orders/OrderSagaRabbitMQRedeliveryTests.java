package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageChannel;
import io.eventdriven.distributedprocesses.core.messaging.MessageRoutes;
import io.eventdriven.distributedprocesses.core.messaging.RabbitMQMessageBus;
import io.eventdriven.testing.RabbitMQConnection;
import io.eventdriven.testing.RabbitMQManagement;
import io.eventdriven.testing.RabbitMQQueues;
import io.eventdriven.testing.TestDatabases;
import io.eventdriven.testing.TestMessages;
import io.eventdriven.testing.TestRabbitMQ;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

public class OrderSagaRabbitMQRedeliveryTests extends OrderSagaBrokerRedeliveryTests {
    private final RabbitMQConnection connection = TestDatabases.rabbitMQ();

    @Autowired
    private ApplicationContext applicationContext;

    private RabbitMQMessageBus messageBus;
    private RabbitMQQueues queues;
    private String collector;

    @Override
    protected MessageBus openBus(List<MessageRoutes> routes, List<MessageChannel> channels) {
        messageBus = TestRabbitMQ.busOn(
                connection, messageContext, applicationContext, TestMessages.serializer(), routes, channels, List.of());
        queues = new RabbitMQQueues(connection);
        collector = queues.collectEverythingPublishedTo(published);

        return messageBus;
    }

    @Override
    protected List<PublishedMessage> awaitPublished(int count) {
        return queues.receiveAll(collector, count).stream()
                .map(message -> new PublishedMessage(
                        message.getMessageProperties().getHeader(RabbitMQMessageBus.TYPE_ID),
                        message.getMessageProperties().getMessageId()))
                .toList();
    }

    @Override
    protected void closeBusAndDeleteChannels() {
        messageBus.close();
        queues.close();
        new RabbitMQManagement(connection).deleteContaining(published);
    }
}
