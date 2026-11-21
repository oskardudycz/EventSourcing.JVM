package io.eventdriven.testing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

public final class RabbitMQQueues implements AutoCloseable {
    private final CachingConnectionFactory connectionFactory;
    private final RabbitAdmin admin;
    private final RabbitTemplate template;
    private final List<String> collectors = new ArrayList<>();

    public RabbitMQQueues(RabbitMQConnection connection) {
        connectionFactory = new CachingConnectionFactory(connection.host(), connection.port());
        connectionFactory.setUsername(connection.username());
        connectionFactory.setPassword(connection.password());
        admin = new RabbitAdmin(connectionFactory);
        template = new RabbitTemplate(connectionFactory);
    }

    public String collectEverythingPublishedTo(String exchange) {
        var queue = "collector-" + UUID.randomUUID();
        var declared = QueueBuilder.durable(queue).build();
        admin.declareQueue(declared);
        collectors.add(queue);
        admin.declareBinding(BindingBuilder.bind(declared)
                .to(new TopicExchange(exchange, true, false))
                .with("#"));
        return queue;
    }

    public List<Message> receiveAll(String queue, int expectedCount) {
        var messages = new ArrayList<Message>();
        var deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();

        while (messages.size() < expectedCount && System.nanoTime() < deadline) {
            var message = template.receive(queue, 500);
            if (message != null) messages.add(message);
        }

        return messages;
    }

    public int messageCount(String queue) {
        return ((Number) admin.getQueueProperties(queue).get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue();
    }

    public void send(String exchange, String routingKey, Message message) {
        template.send(exchange, routingKey, message);
    }

    @Override
    public void close() {
        collectors.forEach(admin::deleteQueue);
        connectionFactory.destroy();
    }
}
