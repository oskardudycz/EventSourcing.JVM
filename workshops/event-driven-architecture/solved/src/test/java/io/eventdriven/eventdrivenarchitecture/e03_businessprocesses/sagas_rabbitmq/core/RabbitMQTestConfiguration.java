package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import com.github.dockerjava.api.model.ContainerPort;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@TestConfiguration(proxyBeanMethods = false)
public class RabbitMQTestConfiguration {
    private static final String DOCKER_COMPOSE_PROJECT = "eventsourcing";
    private static final RabbitMQContainer shared = new RabbitMQContainer("rabbitmq:4.4.0-management");

    @Bean
    DynamicPropertyRegistrar rabbitMQProperties() {
        return registry -> {
            var composePort = dockerComposePort("rabbitmq", 5672);
            if (composePort.isPresent()) {
                registry.add("spring.rabbitmq.host", () -> "localhost");
                registry.add("spring.rabbitmq.port", composePort::get);
                return;
            }

            var container = sharedContainer();
            registry.add("spring.rabbitmq.host", container::getHost);
            registry.add("spring.rabbitmq.port", container::getAmqpPort);
            registry.add("spring.rabbitmq.username", container::getAdminUsername);
            registry.add("spring.rabbitmq.password", container::getAdminPassword);
        };
    }

    private static synchronized RabbitMQContainer sharedContainer() {
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
