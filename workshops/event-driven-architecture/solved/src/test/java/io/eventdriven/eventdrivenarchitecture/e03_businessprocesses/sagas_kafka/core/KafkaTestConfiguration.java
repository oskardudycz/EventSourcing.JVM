package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import com.github.dockerjava.api.model.ContainerPort;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestConfiguration {
    private static final String DOCKER_COMPOSE_PROJECT = "eventsourcing";
    private static final ConfluentKafkaContainer shared =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.4.1"));

    @Bean
    DynamicPropertyRegistrar kafkaProperties() {
        return registry -> registry.add(
                "spring.kafka.bootstrap-servers",
                () -> dockerComposePort("kafka", 9092)
                        .map("localhost:%d"::formatted)
                        .orElseGet(() -> sharedContainer().getBootstrapServers()));
    }

    private static synchronized ConfluentKafkaContainer sharedContainer() {
        if (!shared.isRunning()) shared.start();

        return shared;
    }

    private static Optional<Integer> dockerComposePort(String service, int containerPort) {
        return DockerClientFactory.instance()
                .client()
                .listContainersCmd()
                .withLabelFilter(Map.of(
                        "com.docker.compose.project", DOCKER_COMPOSE_PROJECT,
                        "com.docker.compose.service", service))
                .exec()
                .stream()
                .flatMap(container -> Arrays.stream(container.getPorts()))
                .filter(port -> Objects.equals(port.getPrivatePort(), containerPort) && port.getPublicPort() != null)
                .map(ContainerPort::getPublicPort)
                .findFirst();
    }
}
