package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.rabbitmq.RabbitMQContainer;

public class RabbitMQTestConfigurationTests {
    private final String dockerComposeProject = "test-" + UUID.randomUUID();

    @Test
    public void usesTheRabbitMQThatDockerComposeRuns() {
        try (var rabbitMQ =
                new RabbitMQContainer("rabbitmq:4.4.0-management").withLabels(dockerComposeLabels("rabbitmq"))) {
            rabbitMQ.start();

            assertThat(propertiesOf(RabbitMQTestConfiguration.rabbitMQPropertiesOf(dockerComposeProject)))
                    .containsEntry("spring.rabbitmq.host", rabbitMQ.getHost())
                    .containsEntry("spring.rabbitmq.port", rabbitMQ.getAmqpPort());
        }
    }

    private Map<String, String> dockerComposeLabels(String service) {
        return Map.of("com.docker.compose.project", dockerComposeProject, "com.docker.compose.service", service);
    }

    private static Map<String, Object> propertiesOf(DynamicPropertyRegistrar registrar) {
        var properties = new HashMap<String, Object>();
        registrar.accept((name, value) -> properties.put(name, value.get()));
        return properties;
    }
}
