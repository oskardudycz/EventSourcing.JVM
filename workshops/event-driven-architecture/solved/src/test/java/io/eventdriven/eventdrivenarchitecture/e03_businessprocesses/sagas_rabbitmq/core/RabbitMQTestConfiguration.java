package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;

@TestConfiguration(proxyBeanMethods = false)
public class RabbitMQTestConfiguration {
  @Bean
  @ServiceConnection
  RabbitMQContainer rabbitMQContainer() {
    return new RabbitMQContainer("rabbitmq:3-management");
  }
}
