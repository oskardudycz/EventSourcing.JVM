package io.eventdriven.core.messaging.kafka;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageChannel;
import io.eventdriven.core.messaging.MessageContext;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.messaging.MessageFailureStrategy;
import io.eventdriven.core.messaging.MessageQueue;
import io.eventdriven.core.messaging.MessageRouter;
import io.eventdriven.core.messaging.MessageRoutes;
import io.eventdriven.core.messaging.MessageSource;
import io.eventdriven.core.messaging.MessageValues;
import io.eventdriven.core.messaging.RoutingPattern;
import io.eventdriven.core.messaging.UnreadableMessageException;
import io.eventdriven.core.messaging.versioning.MessageSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.nio.charset.StandardCharsets;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.header.internals.RecordHeaders;
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
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
import org.springframework.kafka.support.mapping.JacksonJavaTypeMapper;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

public class KafkaMessageBus implements MessageBus, SmartLifecycle, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageBus.class);

    public static final String MESSAGE_ID = "messageId";
    public static final String TYPE_ID = DefaultJacksonJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME;

    public static final String DEAD_LETTERED = "messaging.dead_lettered";

    private static final int PARTITIONS = 3;
    private static final int REPLICAS = 1;
    private static final String ALL_MESSAGES = "#";
    private static final long ROUTING_RETRY_INTERVAL_MILLIS = 1000;
    private static final Duration CATCH_UP_CHECK_INTERVAL = Duration.ofMillis(50);
    private static final Duration STOP_TIMEOUT = Duration.ofSeconds(30);

    public record FailureHandling(
            MessageFailureStrategy strategy,
            BackOff backOff,
            MeterRegistry meters,
            KafkaTemplate<String, byte[]> deadLetterTemplate) {}

    private final MessageContext context;
    private final ObservationRegistry observations;
    private final MessageSerializer serializer;
    private final JacksonJsonSerializer<Object> jsonSerializer;
    private final KafkaVersionedMessageConverter messageConverter;
    private final Duration publishTimeout;
    private final FailureHandling failures;
    private final KafkaAdmin admin;
    private final Admin catchUpClient;
    private final Map<String, Object> consumerConfig;
    private final DefaultKafkaProducerFactory<String, byte[]> producerFactory;
    private final KafkaTemplate<String, byte[]> template;
    private final KafkaTemplate<String, byte[]> routingTemplate;

    private final MessageRouter router;
    private final Set<String> channels = ConcurrentHashMap.newKeySet();
    private final List<Consumer<Object>> middlewares = new CopyOnWriteArrayList<>();
    private final Map<String, Map<Class<?>, List<Consumer<MessageEnvelope<Object>>>>> queues =
            new ConcurrentHashMap<>();
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
        var typeMapper = typeMapperOf(serializer);
        this.jsonSerializer = jsonSerializerOf(serializer, typeMapper);
        this.messageConverter = new KafkaVersionedMessageConverter(deserializerOf(serializer, typeMapper), serializer);
        this.publishTimeout = publishTimeout;
        this.failures = failures;
        this.admin = new KafkaAdmin(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        this.catchUpClient = Admin.create(admin.getConfigurationProperties());
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
        this.router = new MessageRouter(List.copyOf(routes));

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
                new KafkaTemplate<>(
                        producerFactory(bootstrapServers, publishTimeout),
                        Map.of(ProducerConfig.CLIENT_ID_CONFIG, "routing")),
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
            var values = context.inheritForPublished(serializer.typeNameOf(message.getClass()), position);

            var headers = new RecordHeaders();
            var body = jsonSerializer.serialize(route.target(), headers, message);
            var record = new ProducerRecord<String, byte[]>(route.target(), null, route.key(), body, headers);
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
    public KafkaMessageBus subscribe(String queue, List<Class<?>> types, Consumer<MessageEnvelope<Object>> handler) {
        for (var type : types)
            queues.computeIfAbsent(queue, ignored -> new ConcurrentHashMap<>())
                    .computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>())
                    .add(handler);
        return this;
    }

    @Override
    public void use(Consumer<Object> middleware) {
        middlewares.add(middleware);
    }

    @Override
    public void whenCaughtUp(Duration timeout) {
        if (!running) return;

        var deadline = Instant.now().plus(timeout);

        whenCaughtUp(routingContainers, deadline);
        whenCaughtUp(
                containers.stream()
                        .filter(container -> !routingContainers.contains(container))
                        .toList(),
                deadline);
    }

    @Override
    public void start() {
        running = true;
        containers.forEach(ConcurrentMessageListenerContainer::start);
    }

    @Override
    public void stop() {
        running = false;
        var stopped = new CountDownLatch(containers.size());
        containers.forEach(container -> container.stop(stopped::countDown));

        try {
            if (!stopped.await(STOP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
                throw new IllegalStateException("Kafka consumers didn't stop within %s".formatted(STOP_TIMEOUT));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stopping the Kafka consumers was interrupted", e);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void close() {
        stop();
        catchUpClient.close();
        producerFactory.destroy();
        routingTemplate.destroy();
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
            var message = messageConverter.read(record);
            var handlers = queues.getOrDefault(queue, Map.of()).get(message.getClass());

            if (handlers == null) return;

            var messageId = Objects.requireNonNullElseGet(
                    header(record, MESSAGE_ID), () -> UUID.randomUUID().toString());

            var metadata = context.received(messageId, new MessageSource.InQueue(queue));

            try (var _ = context.handling("handle " + message.getClass().getSimpleName(), metadata)) {
                for (var handle : handlers) handle.accept(new MessageEnvelope<>(message, metadata));
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
                                    knownTypeNameOf(record),
                                    "messaging.destination.name",
                                    queue)
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
        try {
            return Optional.of(messageConverter.read(record));
        } catch (UnreadableMessageException e) {
            return Optional.empty();
        }
    }

    private String knownTypeNameOf(ConsumerRecord<?, ?> record) {
        var typeName = header(record, TYPE_ID);

        return typeName != null && serializer.isKnown(typeName) ? typeName : UnreadableMessageException.UNKNOWN_TYPE;
    }

    private static String typeNameOf(ConsumerRecord<?, ?> record) {
        return Objects.requireNonNullElse(header(record, TYPE_ID), UnreadableMessageException.UNKNOWN_TYPE);
    }

    private static DefaultJacksonJavaTypeMapper typeMapperOf(MessageSerializer serializer) {
        var typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.TYPE_ID);
        typeMapper.setIdClassMapping(serializer.typeMapper().explicitNames());
        typeMapper.addTrustedPackages(serializer.typeMapper().trustedPackages());

        return typeMapper;
    }

    private static JacksonJsonSerializer<Object> jsonSerializerOf(
            MessageSerializer serializer, DefaultJacksonJavaTypeMapper typeMapper) {
        var jsonSerializer = new JacksonJsonSerializer<>(serializer.jsonMapper());
        jsonSerializer.setTypeMapper(typeMapper);

        return jsonSerializer;
    }

    private static JacksonJsonDeserializer<Object> deserializerOf(
            MessageSerializer serializer, DefaultJacksonJavaTypeMapper typeMapper) {
        var deserializer = new JacksonJsonDeserializer<>(serializer.jsonMapper());
        deserializer.setTypeMapper(typeMapper);
        deserializer.setRemoveTypeHeaders(false);

        return deserializer;
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
    // One request reads the end offsets of every topic and one reads the committed offsets of every group.
    private void whenCaughtUp(List<ConcurrentMessageListenerContainer<String, byte[]>> containers, Instant deadline) {
        if (containers.isEmpty()) return;

        var topicsByGroup = containers.stream()
                .collect(Collectors.toMap(
                        ConcurrentMessageListenerContainer::getGroupId,
                        container -> container.getContainerProperties().getTopics()[0],
                        (first, second) -> first,
                        LinkedHashMap::new));
        var endOffsets = endOffsetsOf(catchUpClient, Set.copyOf(topicsByGroup.values()));
        var checks = new RetryTemplate(RetryPolicy.builder()
                .includes(NotCaughtUpException.class)
                .backOff(new FixedBackOff(CATCH_UP_CHECK_INTERVAL.toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS))
                .timeout(Duration.between(Instant.now(), deadline))
                .build());

        try {
            checks.invoke(() -> {
                var behind = groupsBehind(catchUpClient, topicsByGroup, endOffsets);
                if (!behind.isEmpty())
                    throw new NotCaughtUpException(behind.getFirst(), topicsByGroup.get(behind.getFirst()));
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

    private static Map<TopicPartition, Long> endOffsetsOf(Admin client, Set<String> topics) {
        var partitions = valueOf(client.describeTopics(topics).allTopicNames()).values().stream()
                .flatMap(topic -> topic.partitions().stream()
                        .map(partition -> new TopicPartition(topic.name(), partition.partition())))
                .collect(Collectors.toMap(partition -> partition, partition -> OffsetSpec.latest()));

        return valueOf(client.listOffsets(partitions).all()).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().offset()));
    }

    private static List<String> groupsBehind(
            Admin client, Map<String, String> topicsByGroup, Map<TopicPartition, Long> endOffsets) {
        var committed = valueOf(client.listConsumerGroupOffsets(topicsByGroup.keySet().stream()
                        .collect(Collectors.toMap(group -> group, group -> new ListConsumerGroupOffsetsSpec())))
                .all());

        return topicsByGroup.entrySet().stream()
                .filter(group ->
                        !hasCommitted(committed.getOrDefault(group.getKey(), Map.of()), group.getValue(), endOffsets))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static boolean hasCommitted(
            Map<TopicPartition, OffsetAndMetadata> committed, String topic, Map<TopicPartition, Long> endOffsets) {
        return endOffsets.entrySet().stream()
                .filter(end -> end.getKey().topic().equals(topic))
                .allMatch(end -> {
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
}
