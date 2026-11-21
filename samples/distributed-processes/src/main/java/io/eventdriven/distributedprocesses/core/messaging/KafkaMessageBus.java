package io.eventdriven.distributedprocesses.core.messaging;

import io.eventdriven.distributedprocesses.core.messaging.versioning.MessageSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

public class KafkaMessageBus implements MessageBus, SmartLifecycle, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageBus.class);

    public static final String MESSAGE_ID = "messageId";
    public static final String MESSAGE_TYPE = "messageType";

    public static final String DEAD_LETTERED = "messaging.dead_lettered";

    private static final int PARTITIONS = 3;
    private static final int REPLICAS = 1;
    private static final String ALL_MESSAGES = "#";
    private static final long ROUTING_RETRY_INTERVAL_MILLIS = 1000;
    private static final String UNKNOWN_TYPE = "unknown";
    private static final Duration CATCH_UP_CHECK_INTERVAL = Duration.ofMillis(50);

    public record FailureHandling(
            MessageFailureStrategy strategy,
            BackOff backOff,
            MeterRegistry meters,
            KafkaTemplate<String, byte[]> deadLetterTemplate) {}

    private final MessageContext context;
    private final ObservationRegistry observations;
    private final MessageSerializer serializer;
    private final Duration publishTimeout;
    private final FailureHandling failures;
    private final KafkaAdmin admin;
    private final Map<String, Object> consumerConfig;
    private final DefaultKafkaProducerFactory<String, byte[]> producerFactory;
    private final KafkaTemplate<String, byte[]> template;
    private final KafkaTemplate<String, byte[]> routingTemplate;

    private final MessageRouter router;
    private final Set<String> channels = ConcurrentHashMap.newKeySet();
    private final List<Consumer<Object>> middlewares = new CopyOnWriteArrayList<>();
    private final Map<String, Map<Class<?>, List<Consumer<Object>>>> queues = new ConcurrentHashMap<>();
    private final List<ConcurrentMessageListenerContainer<String, byte[]>> containers = new CopyOnWriteArrayList<>();
    private final List<ConcurrentMessageListenerContainer<String, byte[]>> routingContainers =
            new CopyOnWriteArrayList<>();
    private volatile boolean running;

    public KafkaMessageBus(
            String bootstrapServers,
            MessageContext context,
            ObservationRegistry observations,
            MessageSerializer serializer,
            Duration publishTimeout,
            FailureHandling failures,
            KafkaTemplate<String, byte[]> routingTemplate,
            List<MessageRoutes> routes,
            List<MessageChannel> channels,
            List<MessageQueue> queues) {
        this.context = context;
        this.observations = observations;
        this.serializer = serializer;
        this.publishTimeout = publishTimeout;
        this.failures = failures;
        this.admin = new KafkaAdmin(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        this.consumerConfig = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false);
        this.producerFactory = producerFactory(bootstrapServers, publishTimeout);
        this.template = new KafkaTemplate<>(producerFactory);
        this.template.setObservationEnabled(true);
        this.template.setObservationRegistry(observations);
        this.routingTemplate = routingTemplate;
        this.router = routerFor(routes);

        channels.forEach(this::declare);
        queues.forEach(this::declare);
    }

    public static KafkaMessageBus connectingTo(
            String bootstrapServers,
            MessageContext context,
            ObservationRegistry observations,
            MessageSerializer serializer,
            Duration publishTimeout,
            FailureHandling failures,
            List<MessageRoutes> routes,
            List<MessageChannel> channels,
            List<MessageQueue> queues) {
        return new KafkaMessageBus(
                bootstrapServers,
                context,
                observations,
                serializer,
                publishTimeout,
                failures,
                new KafkaTemplate<>(producerFactory(bootstrapServers, publishTimeout)),
                routes,
                channels,
                queues);
    }

    public static DefaultKafkaProducerFactory<String, byte[]> producerFactory(
            String bootstrapServers, Duration publishTimeout) {
        var share = publishTimeout.dividedBy(5).multipliedBy(2).toMillis();

        return new DefaultKafkaProducerFactory<>(
                Map.of(
                        CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                        bootstrapServers,
                        ProducerConfig.MAX_BLOCK_MS_CONFIG,
                        share,
                        ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,
                        (int) share,
                        ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,
                        (int) share / 2),
                new StringSerializer(),
                new ByteArraySerializer());
    }

    @Override
    public <Message> void publish(List<Message> messages) {
        var prepared = new ArrayList<Prepared>();

        for (var position = 0; position < messages.size(); position++) {
            Object message = messages.get(position);
            for (var middleware : middlewares) middleware.accept(message);

            var route = router.declaredRouteFor(message, channels);
            var values = context.inheritForPublished(message.getClass(), position);

            var record = new ProducerRecord<String, byte[]>(route.target(), route.key(), serializer.write(message));
            record.headers().add(MESSAGE_TYPE, bytesOf(serializer.typeNameOf(message.getClass())));
            record.headers().add(MESSAGE_ID, bytesOf(values.messageId()));
            prepared.add(new Prepared(record, values));
        }

        for (var message : prepared) {
            try (var _ = context.publishing(message.values())) {
                send(template, message.record());
            }
        }
    }

    private record Prepared(ProducerRecord<String, byte[]> record, MessageValues values) {}

    @Override
    public <Message> KafkaMessageBus subscribe(String queue, Class<Message> messageClass, Consumer<Message> handler) {
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
    public void awaitCaughtUp(Duration timeout) {
        if (!running) return;

        var deadline = Instant.now().plus(timeout);

        try (var client = Admin.create(admin.getConfigurationProperties())) {
            for (var container : routingContainers) awaitCaughtUp(client, container, deadline);

            for (var container : containers)
                if (!routingContainers.contains(container)) awaitCaughtUp(client, container, deadline);
        }
    }

    @Override
    public void start() {
        running = true;
        containers.forEach(ConcurrentMessageListenerContainer::start);
    }

    @Override
    public void stop() {
        running = false;
        containers.forEach(ConcurrentMessageListenerContainer::stop);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        stop();
        producerFactory.destroy();
    }

    private void declare(MessageChannel channel) {
        channels.add(channel.name());
        declareTopic(channel.name());
    }

    private void declare(MessageQueue queue) {
        declareTopic(deadLetterTopicOf(queue.name()));

        if (queue.routingPattern().equals(ALL_MESSAGES)) {
            addContainer(queue.name(), queue.channel());
            return;
        }

        declareTopic(queue.name());
        addContainer(queue.name(), queue.name());
        addRoutingContainer(queue.name(), queue.channel(), queue.routingPattern());
    }

    private MessageListener<String, byte[]> queueListener(String queue) {
        return record -> {
            var typeName = header(record, MESSAGE_TYPE);
            if (typeName == null) throw new UnreadableMessageException(UNKNOWN_TYPE, "the record has no type header");

            var message = serializer.read(typeName, record.value());
            var handlers = queues.getOrDefault(queue, Map.of()).get(message.getClass());

            if (handlers == null) return;

            var messageId = Objects.requireNonNullElseGet(
                    header(record, MESSAGE_ID), () -> UUID.randomUUID().toString());

            try (var _ = context.consuming("handle " + message.getClass().getSimpleName(), messageId)) {
                for (var handle : handlers) handle.accept(message);
            }
        };
    }

    private void addRoutingContainer(String queue, String channel, String routingPattern) {
        MessageListener<String, byte[]> listener = record -> {
            if (!RoutingPattern.matches(routingPattern, record.key())) return;

            try {
                send(
                        routingTemplate,
                        new ProducerRecord<>(queue, null, record.key(), record.value(), record.headers()));
            } catch (RuntimeException e) {
                logger.error(
                        "Routing a message with key '{}' from channel '{}' to queue '{}' failed; it will be retried",
                        record.key(),
                        channel,
                        queue,
                        e);
                throw e;
            }
        };

        var container = container("%s-%s-routing".formatted(queue, channel), channel, listener);
        container.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(ROUTING_RETRY_INTERVAL_MILLIS, FixedBackOff.UNLIMITED_ATTEMPTS)));
        routingContainers.add(container);
        register(container);
    }

    private void addContainer(String queue, String topic) {
        var container = container(queue, topic, queueListener(queue));
        container.getContainerProperties().setObservationEnabled(true);
        container.getContainerProperties().setObservationRegistry(observations);
        container.getContainerProperties().setDeliveryAttemptHeader(true);
        container.setCommonErrorHandler(deadLetteringErrorHandler(queue));
        register(container);
    }

    private DefaultErrorHandler deadLetteringErrorHandler(String queue) {
        var deadLetters = new DeadLetterPublishingRecoverer(
                failures.deadLetterTemplate(), (record, error) -> new TopicPartition(deadLetterTopicOf(queue), -1));

        var errorHandler = new DefaultErrorHandler(
                (record, error) -> {
                    deadLetters.accept(record, error);
                    failures.meters()
                            .counter(
                                    DEAD_LETTERED,
                                    "message.type",
                                    registeredTypeNameOf(record),
                                    "messaging.destination.name",
                                    record.topic())
                            .increment();
                },
                failures.backOff());
        errorHandler.setClassifications(Map.of(), true);
        errorHandler.setBackOffFunction(
                (record, error) -> failures.strategy().onFailure(typeNameOf(record), readMessage(record), error)
                                == MessageFailureStrategy.Decision.DEAD_LETTER
                        ? new FixedBackOff(0, 0)
                        : null);
        return errorHandler;
    }

    private Optional<Object> readMessage(ConsumerRecord<?, ?> record) {
        var typeName = header(record, MESSAGE_TYPE);
        if (typeName == null || !(record.value() instanceof byte[] body)) return Optional.empty();

        try {
            return Optional.of(serializer.read(typeName, body));
        } catch (UnreadableMessageException e) {
            return Optional.empty();
        }
    }

    private String registeredTypeNameOf(ConsumerRecord<?, ?> record) {
        var typeName = header(record, MESSAGE_TYPE);

        return typeName != null && serializer.isRegistered(typeName) ? typeName : UNKNOWN_TYPE;
    }

    private static String typeNameOf(ConsumerRecord<?, ?> record) {
        return Objects.requireNonNullElse(header(record, MESSAGE_TYPE), UNKNOWN_TYPE);
    }

    private static String deadLetterTopicOf(String queue) {
        return queue + "-dlt";
    }

    private ConcurrentMessageListenerContainer<String, byte[]> container(
            String groupId, String topic, MessageListener<String, byte[]> listener) {
        var properties = new ContainerProperties(topic);
        properties.setGroupId(groupId);
        properties.setMessageListener(listener);

        return new ConcurrentMessageListenerContainer<>(
                new DefaultKafkaConsumerFactory<>(
                        consumerConfig, new StringDeserializer(), new ByteArrayDeserializer()),
                properties);
    }

    private void register(ConcurrentMessageListenerContainer<String, byte[]> container) {
        containers.add(container);
        if (running) container.start();
    }

    // A consumer has caught up when its group's committed offsets reach the end offsets taken at the start of the wait.
    // Kafka has no push notification for that, so the check is retried after a short delay until the deadline.
    private static void awaitCaughtUp(
            Admin client, ConcurrentMessageListenerContainer<String, byte[]> container, Instant deadline) {
        var group = container.getGroupId();
        var topic = container.getContainerProperties().getTopics()[0];
        var endOffsets = endOffsetsOf(client, topic);
        var checks = new RetryTemplate(RetryPolicy.builder()
                .includes(NotCaughtUpException.class)
                .backOff(new FixedBackOff(CATCH_UP_CHECK_INTERVAL.toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS))
                .timeout(Duration.between(Instant.now(), deadline))
                .build());

        try {
            checks.invoke(() -> {
                if (!hasCommitted(client, group, endOffsets)) throw new NotCaughtUpException(group, topic);
            });
        } catch (NotCaughtUpException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static final class NotCaughtUpException extends RuntimeException {
        NotCaughtUpException(String group, String topic) {
            super("Consumer group '%s' didn't catch up with topic '%s' in time".formatted(group, topic));
        }
    }

    private static Map<TopicPartition, Long> endOffsetsOf(Admin client, String topic) {
        var partitions = valueOf(client.describeTopics(List.of(topic)).allTopicNames()).get(topic).partitions().stream()
                .collect(Collectors.toMap(
                        partition -> new TopicPartition(topic, partition.partition()),
                        partition -> OffsetSpec.latest()));

        return valueOf(client.listOffsets(partitions).all()).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().offset()));
    }

    private static boolean hasCommitted(Admin client, String group, Map<TopicPartition, Long> endOffsets) {
        var committed = valueOf(client.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata());

        return endOffsets.entrySet().stream().allMatch(end -> {
            var offset = committed.get(end.getKey());
            return end.getValue() == 0 || (offset != null && offset.offset() >= end.getValue());
        });
    }

    private static <T> T valueOf(KafkaFuture<T> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException cause
                    ? cause
                    : new KafkaException("Reading the consumer offsets failed", e.getCause());
        } catch (InterruptedException e) {
            throw new InterruptException(e);
        }
    }

    private void declareTopic(String name) {
        admin.createOrModifyTopics(TopicBuilder.name(name)
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build());
    }

    private void send(KafkaTemplate<String, byte[]> sender, ProducerRecord<String, byte[]> record) {
        try {
            sender.send(record).get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException cause
                    ? cause
                    : new KafkaException("Failed to send message", e.getCause());
        } catch (TimeoutException e) {
            throw new KafkaException("No acknowledgement from Kafka within " + publishTimeout, e);
        } catch (InterruptedException e) {
            throw new InterruptException(e);
        }
    }

    private static @Nullable String header(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);

        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }

    private static byte[] bytesOf(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static MessageRouter routerFor(List<MessageRoutes> routes) {
        return new MessageRouter(List.copyOf(routes), message -> new RoutingTarget(null, null));
    }
}
