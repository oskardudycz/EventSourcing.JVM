package io.eventdriven.distributedprocesses.core.messaging;

import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.support.RabbitExceptionTranslator;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

public class RabbitMQMessageBus implements MessageBus, SmartLifecycle, AutoCloseable {
    public static final String MESSAGE_TYPE = "messageType";

    public static final String DEAD_LETTERED = "messaging.dead_lettered";

    private static final String UNKNOWN_TYPE = "unknown";
    private static final Set<String> MANDATORY_CHANNEL_SUFFIXES = Set.of("-requests", "-internal");

    public record FailureHandling(MessageFailureStrategy strategy, BackOff backOff, MeterRegistry meters) {}

    private final MessageContext context;
    private final ApplicationContext applicationContext;
    private final MessageSerializer serializer;
    private final Duration publishTimeout;
    private final FailureHandling failures;
    private final CachingConnectionFactory connectionFactory;
    private final RabbitAdmin admin;
    private final RabbitTemplate template;
    private final RabbitTemplate mandatoryTemplate;

    private final List<MessageRoutes> routes = new ArrayList<>();
    private MessageRouter router = routerFor(routes);
    private final Set<String> channels = ConcurrentHashMap.newKeySet();
    private final List<Consumer<Object>> middlewares = new CopyOnWriteArrayList<>();
    private final Map<String, Map<Class<?>, List<Consumer<Object>>>> queues = new ConcurrentHashMap<>();
    private final Map<String, SimpleMessageListenerContainer> containers = new ConcurrentHashMap<>();
    private volatile boolean running;

    public RabbitMQMessageBus(
            CachingConnectionFactory connectionFactory,
            MessageContext context,
            ApplicationContext applicationContext,
            MessageSerializer serializer,
            Duration publishTimeout,
            FailureHandling failures) {
        this.connectionFactory = connectionFactory;
        this.context = context;
        this.applicationContext = applicationContext;
        this.serializer = serializer;
        this.publishTimeout = publishTimeout;
        this.failures = failures;

        this.admin = new RabbitAdmin(connectionFactory);
        this.admin.setApplicationContext(applicationContext);
        this.admin.setRedeclareManualDeclarations(true);
        this.admin.afterPropertiesSet();

        this.template = publisher(connectionFactory, applicationContext, false);
        this.mandatoryTemplate = publisher(connectionFactory, applicationContext, true);
    }

    public static RabbitMQMessageBus connectingTo(
            String host,
            int port,
            String username,
            String password,
            MessageContext context,
            ApplicationContext applicationContext,
            MessageSerializer serializer,
            Duration publishTimeout,
            FailureHandling failures) {
        var connectionFactory = new CachingConnectionFactory(host, port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);

        return new RabbitMQMessageBus(
                connectionFactory, context, applicationContext, serializer, publishTimeout, failures);
    }

    @Override
    public <Message> void publish(List<Message> messages) {
        var prepared = new ArrayList<Prepared>();

        for (var position = 0; position < messages.size(); position++) {
            Object message = messages.get(position);
            for (var middleware : middlewares) middleware.accept(message);

            var route = router.declaredRouteFor(message, channels);
            var values = context.inheritForPublished(message.getClass(), position);

            prepared.add(new Prepared(route, amqpMessageOf(message, values), values));
        }

        for (var message : prepared) {
            try (var _ = context.publishing(message.values())) {
                send(message.route(), message.message());
            }
        }
    }

    private record Prepared(RoutingTarget route, Message message, MessageValues values) {}

    @Override
    public RabbitMQMessageBus routes(MessageRoutes moduleRoutes) {
        routes.add(moduleRoutes);
        router = routerFor(routes);
        return this;
    }

    @Override
    public RabbitMQMessageBus channel(String name) {
        channels.add(name);
        declareExchange(name);
        return this;
    }

    @Override
    public RabbitMQMessageBus bind(String queue, String channel, String routingPattern) {
        declareExchange(channel);

        declareDeadLetterQueue(queue);

        var declared = QueueBuilder.durable(queue)
                .quorum()
                .deadLetterExchange(deadLetterExchangeOf(queue))
                .deadLetterRoutingKey(queue)
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
        admin.declareQueue(declared);
        admin.declareBinding(
                BindingBuilder.bind(declared).to(topicExchange(channel)).with(routingPattern));

        containers.computeIfAbsent(queue, this::createContainer);
        return this;
    }

    @Override
    public <Message> RabbitMQMessageBus subscribe(
            String queue, Class<Message> messageClass, Consumer<Message> handler) {
        queues.computeIfAbsent(queue, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(messageClass, ignored -> new CopyOnWriteArrayList<>())
                .add(message -> handler.accept(messageClass.cast(message)));
        return this;
    }

    @Override
    public void use(Consumer<Object> middleware) {
        middlewares.add(middleware);
    }

    @Override
    public void start() {
        running = true;
        containers.values().forEach(SimpleMessageListenerContainer::start);
    }

    @Override
    public void stop() {
        running = false;
        containers.values().forEach(SimpleMessageListenerContainer::stop);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        stop();
        connectionFactory.destroy();
    }

    private MessageListener queueListener(String queue) {
        return amqpMessage -> {
            var backOff = failures.backOff().start();

            while (true) {
                try {
                    handle(queue, amqpMessage);
                    return;
                } catch (RuntimeException error) {
                    var interval = nextAttemptIn(queue, amqpMessage, backOff, error);
                    pause(interval, error);
                }
            }
        };
    }

    private void handle(String queue, Message amqpMessage) {
        var typeName = amqpMessage.getMessageProperties().<String>getHeader(MESSAGE_TYPE);
        if (typeName == null) throw new UnreadableMessageException(UNKNOWN_TYPE, "the message has no type header");

        var message = serializer.read(typeName, amqpMessage.getBody());
        var handlers = queues.getOrDefault(queue, Map.of()).get(message.getClass());

        if (handlers == null) return;

        var messageId = Objects.requireNonNullElseGet(
                amqpMessage.getMessageProperties().getMessageId(),
                () -> UUID.randomUUID().toString());

        try (var _ = context.consuming("handle " + message.getClass().getSimpleName(), messageId)) {
            for (var handle : handlers) handle.accept(message);
        }
    }

    private long nextAttemptIn(String queue, Message amqpMessage, BackOffExecution backOff, RuntimeException error) {
        var typeName = amqpMessage.getMessageProperties().<String>getHeader(MESSAGE_TYPE);
        var decision = failures.strategy().onFailure(typeNameOf(typeName), readMessage(typeName, amqpMessage), error);
        var interval =
                decision == MessageFailureStrategy.Decision.RETRY ? backOff.nextBackOff() : BackOffExecution.STOP;

        if (interval == BackOffExecution.STOP) throw deadLetter(queue, typeName, error);

        return interval;
    }

    private AmqpRejectAndDontRequeueException deadLetter(String queue, String typeName, RuntimeException error) {
        failures.meters()
                .counter(
                        DEAD_LETTERED,
                        "message.type",
                        typeName != null && serializer.isRegistered(typeName) ? typeName : UNKNOWN_TYPE,
                        "messaging.destination.name",
                        queue)
                .increment();

        return new AmqpRejectAndDontRequeueException("The message is dead-lettered from queue " + queue, error);
    }

    private Optional<Object> readMessage(String typeName, Message amqpMessage) {
        if (typeName == null) return Optional.empty();

        try {
            return Optional.of(serializer.read(typeName, amqpMessage.getBody()));
        } catch (UnreadableMessageException e) {
            return Optional.empty();
        }
    }

    private static String typeNameOf(String typeName) {
        return Objects.requireNonNullElse(typeName, UNKNOWN_TYPE);
    }

    private static void pause(long interval, RuntimeException error) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw error;
        }
    }

    private SimpleMessageListenerContainer createContainer(String queue) {
        var container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queue);
        container.setMessageListener(queueListener(queue));
        container.setApplicationContext(applicationContext);
        container.setObservationEnabled(true);
        container.setAutoDeclare(false);
        container.setMissingQueuesFatal(false);
        if (running) container.start();
        return container;
    }

    private Message amqpMessageOf(Object message, MessageValues values) {
        var properties = MessagePropertiesBuilder.newInstance()
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(values.messageId())
                .setHeader(MESSAGE_TYPE, serializer.typeNameOf(message.getClass()))
                .build();

        return new Message(serializer.write(message), properties);
    }

    private void declareDeadLetterQueue(String queue) {
        var exchange = new DirectExchange(deadLetterExchangeOf(queue), true, false);
        var deadLetters = QueueBuilder.durable(queue + "-dlq").quorum().build();

        admin.declareExchange(exchange);
        admin.declareQueue(deadLetters);
        admin.declareBinding(BindingBuilder.bind(deadLetters).to(exchange).with(queue));
    }

    private static String deadLetterExchangeOf(String queue) {
        return queue + "-dlx";
    }

    private void declareExchange(String name) {
        admin.declareExchange(topicExchange(name));
    }

    private static TopicExchange topicExchange(String name) {
        return new TopicExchange(name, true, false);
    }

    private void send(RoutingTarget route, Message message) {
        var sender = isMandatory(route.target()) ? mandatoryTemplate : template;
        var correlation = new CorrelationData();

        // Waiting for the confirm inside invoke returns the channel to the cache on this thread. Otherwise Spring AMQP
        // returns it later on the client's thread, which races with close().
        sender.invoke(operations -> {
            operations.send(route.target(), route.key(), message, correlation);
            waitForConfirms(operations);
            return null;
        });

        failIfReturned(route.target(), correlation);
    }

    private void waitForConfirms(RabbitOperations operations) {
        try {
            operations.waitForConfirmsOrDie(publishTimeout.toMillis());
        } catch (AmqpException e) {
            throw e;
        } catch (RuntimeException e) {
            throw RabbitExceptionTranslator.convertRabbitAccessException(e);
        }
    }

    private static void failIfReturned(String channel, CorrelationData correlation) {
        var returned = correlation.getReturned();
        if (returned != null)
            throw new AmqpException("RabbitMQ could not route the message to channel '%s': %s"
                    .formatted(channel, returned.getReplyText()));
    }

    private RabbitTemplate publisher(
            CachingConnectionFactory connectionFactory, ApplicationContext applicationContext, boolean mandatory) {
        var publisher = new RabbitTemplate(connectionFactory);
        publisher.setUsePublisherConnection(true);
        publisher.setMandatory(mandatory);
        publisher.setObservationEnabled(true);
        publisher.setApplicationContext(applicationContext);
        return publisher;
    }

    private static boolean isMandatory(String channel) {
        return MANDATORY_CHANNEL_SUFFIXES.stream().anyMatch(channel::endsWith);
    }

    private static MessageRouter routerFor(List<MessageRoutes> routes) {
        return new MessageRouter(List.copyOf(routes), message -> new RoutingTarget(null, null));
    }
}
