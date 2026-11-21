package io.eventdriven.testing;

import io.eventdriven.distributedprocesses.core.messaging.KafkaMessageBus;
import io.eventdriven.distributedprocesses.core.messaging.MessageFailureStrategy;
import io.eventdriven.distributedprocesses.core.processing.RetryBackOff;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import org.springframework.kafka.core.KafkaTemplate;

public final class TestKafka {
    public static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration RETRY_INTERVAL = Duration.ofMillis(10);

    private TestKafka() {}

    public static KafkaMessageBus.FailureHandling failureHandling(
            String bootstrapServers, ObservationRegistry observations) {
        return failureHandling(bootstrapServers, observations, new SimpleMeterRegistry(), PUBLISH_TIMEOUT);
    }

    public static KafkaMessageBus.FailureHandling failureHandling(
            String bootstrapServers, ObservationRegistry observations, MeterRegistry meters, Duration publishTimeout) {
        var deadLetterTemplate = new KafkaTemplate<>(KafkaMessageBus.producerFactory(bootstrapServers, publishTimeout));
        deadLetterTemplate.setObservationEnabled(true);
        deadLetterTemplate.setObservationRegistry(observations);

        return failureHandling(meters, deadLetterTemplate);
    }

    public static KafkaMessageBus.FailureHandling failureHandling(
            MeterRegistry meters, KafkaTemplate<String, byte[]> deadLetterTemplate) {
        return new KafkaMessageBus.FailureHandling(
                MessageFailureStrategy.retryUnlessUnreadable(),
                RetryBackOff.exponential(RETRY_INTERVAL),
                meters,
                deadLetterTemplate);
    }
}
