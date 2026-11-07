package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageFailureStrategy;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RetryBackOff;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageFailureStrategy.Decision.DEAD_LETTER;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.getMessageType;

@Configuration
@EnableConfigurationProperties(KafkaMessageBusProperties.class)
public class KafkaConfiguration {
    @Bean
    public NewTopic hotelFinancialTopic(KafkaMessageBusProperties properties) {
        return TopicBuilder.name(properties.topic())
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic hotelFinancialDeadLetterTopic(KafkaMessageBusProperties properties) {
        return TopicBuilder.name(properties.topic() + "-dlt")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public MessageFailureStrategy messageFailureStrategy() {
        return MessageFailureStrategy.retryUnlessUnreadable();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
        ProducerFactory<?, ?> producerFactory,
        MessageFailureStrategy failureStrategy,
        VersionedMessageConverter messageConverter,
        MeterRegistry meterRegistry,
        @Value("${app.messaging.retry.initial-interval:1s}") Duration initialInterval
    ) {
        var deadLetterPublisher = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate(producerFactory));

        var errorHandler = new DefaultErrorHandler(
            (record, error) -> {
                deadLetterPublisher.accept(record, error);
                meterRegistry.counter("messaging.dead_lettered",
                    "message.type", Objects.requireNonNullElse(getMessageType(record), "unknown"),
                    "messaging.destination.name", record.topic()
                ).increment();
            },
            RetryBackOff.exponential(initialInterval)
        );
        errorHandler.setClassifications(Map.of(), true);
        errorHandler.setBackOffFunction((record, error) ->
            failureStrategy.onFailure(getMessageType(record), readMessage(messageConverter, record), error) == DEAD_LETTER
                ? new FixedBackOff(0, 0)
                : null
        );
        return errorHandler;
    }

    private static KafkaTemplate<String, byte[]> deadLetterKafkaTemplate(ProducerFactory<?, ?> producerFactory) {
        var template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
            producerFactory.getConfigurationProperties(),
            new StringSerializer(),
            new ByteArraySerializer()
        ));
        template.setObservationEnabled(true);
        return template;
    }

    private static Optional<Object> readMessage(VersionedMessageConverter messageConverter, ConsumerRecord<?, ?> record) {
        try {
            return Optional.of(messageConverter.read(record));
        } catch (UnreadableMessageException e) {
            return Optional.empty();
        }
    }

    @Bean
    public ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>> deliveryAttemptHeader() {
        return container -> container.getContainerProperties().setDeliveryAttemptHeader(true);
    }
}
