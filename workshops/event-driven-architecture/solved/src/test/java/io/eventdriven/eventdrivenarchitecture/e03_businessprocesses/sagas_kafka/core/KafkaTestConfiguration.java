package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;


@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestConfiguration {
  @Bean
  @ServiceConnection
  KafkaContainer kafkaContainer() {
    return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"))
      .withReuse(true);
  }
}
