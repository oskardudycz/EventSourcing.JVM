package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

public class KafkaTestConfigurationTests {
    private final String dockerComposeProject = "test-" + UUID.randomUUID();

    @Test
    public void usesTheKafkaThatDockerComposeRuns() {
        try (var kafka = new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:8.4.1"))
                .withLabels(dockerComposeLabels("kafka"))) {
            kafka.start();

            assertThat(propertiesOf(KafkaTestConfiguration.kafkaPropertiesOf(dockerComposeProject)))
                    .containsEntry("spring.kafka.bootstrap-servers", kafka.getBootstrapServers());
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
