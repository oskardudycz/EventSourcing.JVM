package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.rabbitmq")
public record RabbitMQMessageBusProperties(
  String exchange,
  Duration publishTimeout
) {
}
