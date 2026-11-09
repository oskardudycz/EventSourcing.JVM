package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.CAUSATION_ID;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.CORRELATION_ID;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.MESSAGE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = KafkaSagaTestApplication.class)
@TestPropertySource(properties = {
  "app.kafka.topic=message-metadata",
  "app.kafka.group-id=message-metadata"
})
@AutoConfigureTracing
public class KafkaMessageMetadataTest {
  public record RoomInspected(String roomId, String notes) {
  }

  @Autowired
  private KafkaMessageBus messageBus;
  @Autowired
  private Tracer tracer;
  @Autowired
  private ConsumerFactory<?, ?> consumerFactory;

  @Value("${app.kafka.topic}")
  private String topic;

  @Test
  public void messageStartingAProcess_CarriesAMessageIdAndAGeneratedCorrelationId() {
    var message = new RoomInspected(UUID.randomUUID().toString(), "clean");

    messageBus.publish(List.of(message));

    var record = publishedRecord(message.roomId());
    assertThat(headerValues(record, MESSAGE_ID)).singleElement().satisfies(UUID::fromString);
    assertThat(headerValues(record, CORRELATION_ID)).singleElement().satisfies(UUID::fromString);
    assertThat(headerValues(record, CAUSATION_ID)).isEmpty();
  }

  @Test
  public void messagePublishedWhileHandlingAnother_CarriesItsCorrelationAndCausationIds() {
    var message = new RoomInspected(UUID.randomUUID().toString(), "clean");

    try (var _ = tracer.createBaggageInScope(CORRELATION_ID, "process-correlation-id");
         var _ = tracer.createBaggageInScope(CAUSATION_ID, "handled-message-id")) {
      messageBus.publish(List.of(message));
    }

    var record = publishedRecord(message.roomId());
    assertThat(headerValues(record, MESSAGE_ID)).singleElement().satisfies(UUID::fromString);
    assertThat(headerValues(record, CORRELATION_ID)).containsExactly("process-correlation-id");
    assertThat(headerValues(record, CAUSATION_ID)).containsExactly("handled-message-id");
  }

  private static List<String> headerValues(ConsumerRecord<String, String> record, String name) {
    return StreamSupport.stream(record.headers().headers(name).spliterator(), false)
      .map(Header::value)
      .map(String::new)
      .toList();
  }

  private ConsumerRecord<String, String> publishedRecord(String containing) {
    return await().atMost(Duration.ofSeconds(10))
      .until(() -> findRecord(containing), Optional::isPresent)
      .get();
  }

  private Optional<ConsumerRecord<String, String>> findRecord(String containing) {
    var overrides = new Properties();
    overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    var rawConsumerFactory = new DefaultKafkaConsumerFactory<>(
      consumerFactory.getConfigurationProperties(),
      new StringDeserializer(),
      new StringDeserializer()
    );

    try (var consumer = rawConsumerFactory.createConsumer("metadata-reader-" + UUID.randomUUID(), null, null, overrides)) {
      consumer.subscribe(List.of(topic));

      for (var record : consumer.poll(Duration.ofSeconds(2))) {
        if (record.value().contains(containing))
          return Optional.of(record);
      }
      return Optional.empty();
    }
  }
}
