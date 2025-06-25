package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.TestApplicationConfig;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TestApplicationConfig.class)
@ComponentScan(basePackages = {
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core",
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core"
})
@TestPropertySource(properties = {"messaging.type=kafka"})
@Import(KafkaTestConfiguration.class)
public class KafkaPropertiesBootstrapTest {

  @Autowired
  private KafkaProperties kafkaProperties;

  @Autowired
  private KafkaConnectionDetails connectionDetails;

  @Test
  public void shouldShowThatBuildProducerPropertiesDoesNotIncludeServiceConnection() {
    Map<String, Object> producerProperties = kafkaProperties.buildProducerProperties(null);

    System.out.println("=== Producer Properties (buildProducerProperties alone) ===");
    producerProperties.forEach((key, value) ->
      System.out.println(key + " = " + value)
    );

    Object bootstrapServersObj = producerProperties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);
    String bootstrapServers = bootstrapServersObj.toString();

    System.out.println("Bootstrap servers from buildProducerProperties: " + bootstrapServers);

    assertThat(bootstrapServers)
      .as("buildProducerProperties() should return default localhost:9092")
      .contains("localhost:9092");
  }

  @Test
  public void shouldShowThatKafkaConnectionDetailsHasCorrectBootstrapServers() {
    List<String> producerBootstrapServers = connectionDetails.getProducerBootstrapServers();

    boolean isPropertiesKafka = connectionDetails.getClass().getName()
      .contains("PropertiesKafkaConnectionDetails");

    System.out.println("=== KafkaConnectionDetails ===");
    System.out.println("Type: " + connectionDetails.getClass().getName());
    System.out.println("Is PropertiesKafkaConnectionDetails: " + isPropertiesKafka);
    System.out.println("Producer bootstrap servers: " + producerBootstrapServers);
    System.out.println("============================");

    assertThat(producerBootstrapServers)
      .as("KafkaConnectionDetails should have Testcontainers bootstrap servers")
      .isNotEmpty()
      .allSatisfy(server -> assertThat(server).doesNotContain("localhost:9092"));
  }

  @Test
  public void shouldProveManuallyApplyingConnectionDetailsWorks() {
    Map<String, Object> props = kafkaProperties.buildProducerProperties(null);

    System.out.println("=== Before applying KafkaConnectionDetails ===");
    System.out.println("Bootstrap servers: " + props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));

    boolean isPropertiesKafka = connectionDetails.getClass().getName()
      .contains("PropertiesKafkaConnectionDetails");

    // Apply connection details manually (Spring Boot's pattern)
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, connectionDetails.getProducerBootstrapServers());
    if (!isPropertiesKafka) {
      props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
    }

    System.out.println("=== After applying KafkaConnectionDetails ===");
    System.out.println("Bootstrap servers: " + props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    System.out.println("Security protocol: " + props.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
    System.out.println("============================================");

    @SuppressWarnings("unchecked")
    List<String> finalBootstrapServers = (List<String>) props.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);

    assertThat(finalBootstrapServers)
      .as("After applying connection details, should have Testcontainers bootstrap servers")
      .isNotEmpty()
      .allSatisfy(server -> assertThat(server).doesNotContain("localhost:9092"));

    assertThat(props.get(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG))
      .as("Security protocol should be set to PLAINTEXT for non-properties connection details")
      .isEqualTo("PLAINTEXT");
  }
}
