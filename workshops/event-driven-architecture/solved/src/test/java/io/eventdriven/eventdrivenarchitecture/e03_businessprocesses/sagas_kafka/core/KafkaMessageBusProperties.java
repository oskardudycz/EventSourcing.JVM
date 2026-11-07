package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.kafka")
public record KafkaMessageBusProperties(
  String topic,
  String groupId,
  Duration publishTimeout
) {
}
