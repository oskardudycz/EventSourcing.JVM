package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.rabbitmq")
public record RabbitMQMessageBusProperties(String exchange, Duration publishTimeout) {}
