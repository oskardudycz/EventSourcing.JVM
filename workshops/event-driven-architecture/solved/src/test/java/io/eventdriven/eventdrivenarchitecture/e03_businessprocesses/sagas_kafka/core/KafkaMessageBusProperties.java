package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.kafka")
public record KafkaMessageBusProperties(String topic, String groupId, Duration publishTimeout) {}
