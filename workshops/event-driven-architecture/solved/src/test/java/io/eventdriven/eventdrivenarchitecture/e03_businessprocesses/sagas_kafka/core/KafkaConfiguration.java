package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageFailureStrategy;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RetryBackOff;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
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
@ConditionalOnProperty(name = "messaging.type", havingValue = "kafka")
public class KafkaConfiguration {
    @Bean
    public NewTopic hotelFinancialTopic(@Value("${app.kafka.topic:hotel-financial}") String topic) {
        return TopicBuilder.name(topic)
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public NewTopic hotelFinancialDeadLetterTopic(@Value("${app.kafka.topic:hotel-financial}") String topic) {
        return TopicBuilder.name(topic + "-dlt")
            .partitions(3)
            .replicas(1)
            .build();
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(
        ProducerFactory<String, Object> producerFactory
    ) {
        var template = new KafkaTemplate<>(producerFactory);
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public MessageFailureStrategy messageFailureStrategy() {
        return MessageFailureStrategy.retryUnlessUnreadable();
    }

    @Bean
    public KafkaTemplate<String, String> deadLetterKafkaTemplate(
        ProducerFactory<String, Object> producerFactory
    ) {
        var template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
            producerFactory.getConfigurationProperties(),
            new StringSerializer(),
            new StringSerializer()
        ));
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
        KafkaTemplate<String, String> deadLetterKafkaTemplate,
        MessageFailureStrategy failureStrategy,
        TransformingMessageDeserializer deserializer,
        MeterRegistry meterRegistry,
        @Value("${app.messaging.retry.initial-interval:1s}") Duration initialInterval
    ) {
        var deadLetterPublisher = new DeadLetterPublishingRecoverer(deadLetterKafkaTemplate);

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
            failureStrategy.onFailure(getMessageType(record), readMessage(deserializer, record), error) == DEAD_LETTER
                ? new FixedBackOff(0, 0)
                : null
        );
        return errorHandler;
    }

    @SuppressWarnings("unchecked")
    private static Optional<Object> readMessage(TransformingMessageDeserializer deserializer, ConsumerRecord<?, ?> record) {
        try {
            return Optional.of(deserializer.deserialize((ConsumerRecord<String, String>) record));
        } catch (UnreadableMessageException e) {
            return Optional.empty();
        }
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String>
        kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler
        ) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setObservationEnabled(true);
        factory.getContainerProperties().setDeliveryAttemptHeader(true);
        return factory;
    }
}
