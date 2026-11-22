package io.eventdriven.core.messaging.rabbitmq;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.MessageFailureStrategy;
import io.eventdriven.core.messaging.MessageMetadata;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRouter;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.MessageSource;
import io.eventdriven.core.messaging.MessageValues;
import io.eventdriven.core.messaging.RoutingTarget;
import io.eventdriven.core.messaging.UnreadableMessageException;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.listener.ListenerExecutionFailedException;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.rabbit.support.RabbitExceptionTranslator;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.util.backoff.BackOff;

public class RabbitMQMessageBus implements MessageBus, SmartLifecycle, AutoCloseable {
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);

    public static final String TYPE_ID = DefaultClassMapper.DEFAULT_CLASSID_FIELD_NAME;

    public static final String DEAD_LETTERED = "messaging.dead_lettered";

    private static final String CATCH_UP_PROBE = "catchUpProbe";
    private static final Set<String> MANDATORY_CHANNEL_SUFFIXES = Set.of("-requests", "-internal");

    public record FailureHandling(MessageFailureStrategy strategy, BackOff backOff, MeterRegistry meters) {}

    private final MessageContext context;
    private final ApplicationContext applicationContext;
    private final MessageSerializer serializer;
    private final JacksonJsonMessageConverter jsonConverter;
    private final RabbitMQVersionedMessageConverter messageConverter;
    private final Duration publishTimeout;
    private final FailureHandling failures;
    private final CachingConnectionFactory connectionFactory;
    private final RabbitAdmin admin;
    private final RabbitTemplate template;
    private final RabbitTemplate mandatoryTemplate;

    private final MessageRouter router;
    private final Set<String> channels = ConcurrentHashMap.newKeySet();
    private final List<Consumer<MessageEnvelope<Object>>> middlewares = new CopyOnWriteArrayList<>();
    private final Map<String, Map<Class<?>, List<Consumer<MessageEnvelope<Object>>>>> queues =
            new ConcurrentHashMap<>();
    private final Map<String, SimpleMessageListenerContainer> containers = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> catchUpProbes = new ConcurrentHashMap<>();
    private volatile boolean running;

    public RabbitMQMessageBus(
            CachingConnectionFactory connectionFactory,
            MessageContext context,
            ApplicationContext applicationContext,
            MessageSerializer serializer,
            Duration publishTimeout,
            FailureHandling failures,
            List<MessageRoutes> routes,
            List<MessageChannel> channels,
            List<MessageQueue> queues) {
        this.connectionFactory = connectionFactory;
        this.context = context;
        this.applicationContext = applicationContext;
        this.serializer = serializer;
        this.jsonConverter = jsonConverterOf(serializer);
        this.messageConverter = new RabbitMQVersionedMessageConverter(jsonConverter, serializer);
        this.publishTimeout = publishTimeout;
        this.failures = failures;

        this.admin = new RabbitAdmin(connectionFactory);
        this.admin.setApplicationContext(applicationContext);
        this.admin.setRedeclareManualDeclarations(true);
        this.admin.afterPropertiesSet();

        this.template = publisher(connectionFactory, applicationContext, false);
        this.mandatoryTemplate = publisher(connectionFactory, applicationContext, true);
        this.router = new MessageRouter(List.copyOf(routes));

        channels.forEach(this::declare);
        queues.forEach(this::declare);
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
            FailureHandling failures,
            List<MessageRoutes> routes,
            List<MessageChannel> channels,
            List<MessageQueue> queues) {
        var connectionFactory = new CachingConnectionFactory(host, port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);

        return new RabbitMQMessageBus(
                connectionFactory,
                context,
                applicationContext,
                serializer,
                publishTimeout,
                failures,
                routes,
                channels,
                queues);
    }

    @Override
    public <Message> void publish(List<Message> messages) {
        var prepared = new ArrayList<Prepared>();

        for (var position = 0; position < messages.size(); position++) {
            Object message = messages.get(position);

            var values = context.inheritForPublished(serializer.typeNameOf(message.getClass()), position);
            var route = routeOf(message, values);
            var published = new MessageEnvelope<>(
                    message, MessageMetadata.of(values, new MessageSource.OnChannel(route.target())));
            for (var middleware : middlewares) middleware.accept(published);

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
    public RabbitMQMessageBus subscribe(String queue, List<Class<?>> types, Consumer<MessageEnvelope<Object>> handler) {
        for (var type : types)
            queues.computeIfAbsent(queue, ignored -> new ConcurrentHashMap<>())
                    .computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>())
                    .add(handler);
        return this;
    }

    @Override
    public void use(Consumer<MessageEnvelope<Object>> middleware) {
        middlewares.add(middleware);
    }

    // Each queue has one consumer that handles its messages in order, so when a probe sent after them comes back,
    // the messages before it are handled or dead-lettered.
    @Override
    public void whenCaughtUp(Duration timeout) {
        if (!running) return;

        var deadline = Instant.now().plus(timeout);
        var probes = new LinkedHashMap<String, String>();

        try {
            for (var queue : containers.keySet()) {
                var probeId = UUID.randomUUID().toString();
                catchUpProbes.put(probeId, new CountDownLatch(1));
                probes.put(queue, probeId);
                send(new RoutingTarget("", queue), catchUpProbe(probeId));
            }

            for (var probe : probes.entrySet())
                if (!catchUpProbes
                        .get(probe.getValue())
                        .await(Duration.between(Instant.now(), deadline).toMillis(), TimeUnit.MILLISECONDS))
                    throw new IllegalStateException("Queue '%s' didn't catch up in time".formatted(probe.getKey()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Waiting for the queues to catch up was interrupted", e);
        } finally {
            probes.values().forEach(catchUpProbes::remove);
        }
    }

    @Override
    public void start() {
        running = true;
        containers.values().forEach(SimpleMessageListenerContainer::start);
    }

    @Override
    public void stop() {
        running = false;
        var stopped = new CountDownLatch(containers.size());
        containers.values().forEach(container -> container.stop(stopped::countDown));

        try {
            if (!stopped.await(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                throw new IllegalStateException("RabbitMQ consumers didn't stop within %s".formatted(STOP_TIMEOUT));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stopping the RabbitMQ consumers was interrupted", e);
        }
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

    private void declare(MessageChannel channel) {
        channels.add(channel.name());
        declareExchange(channel.name());
    }

    private void declare(MessageQueue queue) {
        declareExchange(queue.channel());

        declareDeadLetterQueue(queue.name());

        var declared = QueueBuilder.durable(queue.name())
                .quorum()
                .deadLetterExchange(deadLetterExchangeOf(queue.name()))
                .deadLetterRoutingKey(queue.name())
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .overflow(QueueBuilder.Overflow.rejectPublish)
                .build();
        admin.declareQueue(declared);
        admin.declareBinding(
                BindingBuilder.bind(declared).to(topicExchange(queue.channel())).with(queue.routingPattern()));

        containers.computeIfAbsent(queue.name(), this::createContainer);
    }

    private MessageListener queueListener(String queue) {
        return amqpMessage -> {
            var probeId = amqpMessage.getMessageProperties().<String>getHeader(CATCH_UP_PROBE);
            if (probeId != null) {
                var probe = catchUpProbes.get(probeId);
                if (probe != null) probe.countDown();
                return;
            }

            try {
                handle(queue, amqpMessage);
            } catch (RuntimeException error) {
                throw new ListenerExecutionFailedException(
                        "The handler of queue %s failed".formatted(queue), error, amqpMessage);
            }
        };
    }

    private void handle(String queue, Message amqpMessage) {
        var message = messageConverter.read(amqpMessage);
        var handlers = queues.getOrDefault(queue, Map.of()).get(message.getClass());

        if (handlers == null) return;

        var messageId = Objects.requireNonNullElseGet(
                amqpMessage.getMessageProperties().getMessageId(),
                () -> UUID.randomUUID().toString());

        var metadata = context.received(messageId, new MessageSource.InQueue(queue));

        try (var _ = context.handling("handle " + message.getClass().getSimpleName(), metadata)) {
            for (var handle : handlers) handle.accept(new MessageEnvelope<>(message, metadata));
        }
    }

    private boolean shouldRetry(Throwable error) {
        var failed = error instanceof ListenerExecutionFailedException failure
                ? Optional.ofNullable(failure.getFailedMessage())
                : Optional.<Message>empty();

        return failures.strategy()
                        .onFailure(
                                typeNameOf(
                                        failed.map(RabbitMQMessageBus::typeIdOf).orElse(null)),
                                failed.flatMap(this::readMessage),
                                error)
                == MessageFailureStrategy.Decision.RETRY;
    }

    private void deadLetter(String queue, Message amqpMessage, Throwable error) {
        var typeName = typeIdOf(amqpMessage);

        failures.meters()
                .counter(
                        DEAD_LETTERED,
                        "message.type",
                        typeName != null && serializer.isKnown(typeName)
                                ? typeName
                                : UnreadableMessageException.UNKNOWN_TYPE,
                        "messaging.destination.name",
                        queue)
                .increment();

        new RejectAndDontRequeueRecoverer().recover(amqpMessage, error);
    }

    private static String typeIdOf(Message amqpMessage) {
        return amqpMessage.getMessageProperties().getHeader(TYPE_ID);
    }

    private Optional<Object> readMessage(Message amqpMessage) {
        try {
            return Optional.of(messageConverter.read(amqpMessage));
        } catch (UnreadableMessageException e) {
            return Optional.empty();
        }
    }

    private static String typeNameOf(String typeName) {
        return Objects.requireNonNullElse(typeName, UnreadableMessageException.UNKNOWN_TYPE);
    }

    private SimpleMessageListenerContainer createContainer(String queue) {
        var container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueueNames(queue);
        container.setMessageListener(queueListener(queue));
        container.setAdviceChain(retryOf(queue));
        container.setApplicationContext(applicationContext);
        container.setObservationEnabled(true);
        container.setAutoDeclare(false);
        container.setMissingQueuesFatal(false);
        if (running) container.start();
        return container;
    }

    private StatelessRetryOperationsInterceptor retryOf(String queue) {
        return RetryInterceptorBuilder.stateless()
                .retryPolicy(RetryPolicy.builder()
                        .backOff(failures.backOff())
                        .predicate(this::shouldRetry)
                        .build())
                .recoverer((amqpMessage, error) -> deadLetter(queue, amqpMessage, error))
                .build();
    }

    private Message amqpMessageOf(Object message, MessageValues values) {
        var properties = MessagePropertiesBuilder.newInstance()
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(values.messageId())
                .build();

        return jsonConverter.toMessage(message, properties);
    }

    private static JacksonJsonMessageConverter jsonConverterOf(MessageSerializer serializer) {
        var classMapper = new DefaultClassMapper();
        classMapper.setIdClassMapping(serializer.typeMapper().explicitNames());
        classMapper.setTrustedPackages(serializer.typeMapper().trustedPackages());
        classMapper.afterPropertiesSet();

        var converter = new JacksonJsonMessageConverter(serializer.jsonMapper());
        converter.setClassMapper(classMapper);

        return converter;
    }

    private static Message catchUpProbe(String probeId) {
        return new Message(
                new byte[0],
                MessagePropertiesBuilder.newInstance()
                        .setHeader(CATCH_UP_PROBE, probeId)
                        .build());
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

    private RoutingTarget routeOf(Object message, MessageValues values) {
        try {
            return router.declaredRouteFor(message, channels);
        } catch (RuntimeException unrouted) {
            var published = new MessageEnvelope<>(message, MessageMetadata.of(values, new MessageSource.Unrouted()));
            for (var middleware : middlewares) middleware.accept(published);
            throw unrouted;
        }
    }
}
