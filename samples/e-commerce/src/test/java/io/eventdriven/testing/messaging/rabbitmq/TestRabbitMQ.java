package io.eventdriven.testing.messaging.rabbitmq;

import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageFailureStrategy;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.rabbitmq.RabbitMQMessageBus;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.eventdriven.core.processing.RetryBackOff;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.springframework.context.ApplicationContext;

public final class TestRabbitMQ {
    public static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration RETRY_INTERVAL = Duration.ofMillis(10);

    private TestRabbitMQ() {}

    public static RabbitMQMessageBus busOn(
            RabbitMQConnection connection,
            MessageContext messageContext,
            ApplicationContext applicationContext,
            MessageSerializer serializer,
            List<MessageRoutes> routes,
            List<MessageChannel> channels,
            List<MessageQueue> queues) {
        return busOn(
                connection,
                messageContext,
                applicationContext,
                serializer,
                new SimpleMeterRegistry(),
                routes,
                channels,
                queues);
    }

    public static RabbitMQMessageBus busOn(
            RabbitMQConnection connection,
            MessageContext messageContext,
            ApplicationContext applicationContext,
            MessageSerializer serializer,
            MeterRegistry meters,
            List<MessageRoutes> routes,
            List<MessageChannel> channels,
            List<MessageQueue> queues) {
        return busOn(
                connection,
                messageContext,
                applicationContext,
                serializer,
                new RabbitMQMessageBus.FailureHandling(
                        MessageFailureStrategy.retryUnlessUnreadable(),
                        RetryBackOff.exponential(RETRY_INTERVAL),
                        meters),
                routes,
                channels,
                queues);
    }

    public static RabbitMQMessageBus busOn(
            RabbitMQConnection connection,
            MessageContext messageContext,
            ApplicationContext applicationContext,
            MessageSerializer serializer,
            RabbitMQMessageBus.FailureHandling failures,
            List<MessageRoutes> routes,
            List<MessageChannel> channels,
            List<MessageQueue> queues) {
        return RabbitMQMessageBus.connectingTo(
                connection.host(),
                connection.port(),
                connection.username(),
                connection.password(),
                messageContext,
                applicationContext,
                serializer,
                PUBLISH_TIMEOUT,
                failures,
                routes,
                channels,
                queues);
    }
}
