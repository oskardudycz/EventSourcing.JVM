package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.InMemorySpansConfiguration;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RecordingFailureStrategy;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RecordingFailureStrategy.Failure;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.TestApplicationConfig;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;
import org.springframework.test.context.TestPropertySource;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = TestApplicationConfig.class)
@ComponentScan(basePackages = {
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core",
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core"
})
@TestPropertySource(properties = {
  "messaging.type=kafka",
  "app.kafka.topic=error-handling",
  "app.kafka.groupId=error-handling",
  "spring.kafka.consumer.auto-offset-reset=earliest",
  "app.messaging.retry.initial-interval=10ms"
})
@Import({
  KafkaTestConfiguration.class,
  InMemorySpansConfiguration.class,
  RecordingFailureStrategy.Config.class
})
@AutoConfigureTracing
@AutoConfigureMetrics
public class KafkaErrorHandlingTest {
  public record PaymentReceived(String id) {
  }

  @Autowired
  private KafkaMessageBus messageBus;

  @Autowired
  private ConsumerFactory<?, ?> consumerFactory;

  @Autowired
  private ProducerFactory<String, Object> producerFactory;

  @Autowired
  private MeterRegistry meterRegistry;

  @Autowired
  private RecordingFailureStrategy failureStrategy;

  @Autowired
  private InMemorySpanExporter spanExporter;

  @Value("${app.kafka.topic}")
  private String topic;

  @BeforeEach
  public void setUp() {
    spanExporter.reset();
    failureStrategy.failures.clear();
  }

  @Test
  public void failingHandler_IsRetriedThenDeadLettered_AndNextMessageIsHandled() {
    var deadLetteredBefore = deadLetteredCount(PaymentReceived.class.getName());
    var poison = new PaymentReceived(UUID.randomUUID().toString());
    var healthy = new PaymentReceived(UUID.randomUUID().toString());
    var attempts = new CopyOnWriteArrayList<String>();

    messageBus.subscribe(PaymentReceived.class, message -> {
      attempts.add(message.id());
      if (message.id().equals(poison.id()))
        throw new IllegalStateException("Payment gateway unavailable for " + message.id());
    });

    messageBus.publish(List.of(poison, healthy));

    var deadLettered = await().atMost(Duration.ofSeconds(30))
      .until(() -> findOnDeadLetterTopic(poison.id()), Optional::isPresent)
      .get();

    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> assertThat(attempts).contains(healthy.id()));

    assertThat(attempts.stream().filter(poison.id()::equals))
      .as("poison message should be attempted 3 times, then dead-lettered")
      .hasSize(3);
    assertThat(deliveryAttempt(deadLettered)).isEqualTo(3);
    assertThat(exceptionCause(deadLettered)).isEqualTo(IllegalStateException.class.getName());
    assertThat(deadLetteredCount(PaymentReceived.class.getName())).isEqualTo(deadLetteredBefore + 1);
    assertThat(failureStrategy.failures)
      .as("the strategy should see the message it decides about")
      .contains(new Failure(PaymentReceived.class.getName(), Optional.of(poison)));
    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> assertThat(failedSpans())
        .as("each failed attempt should be visible in the trace")
        .hasSize(3));
  }

  @Test
  public void messageOfUnknownType_IsDeadLetteredWithoutRetry_AndNextMessageIsHandled() {
    var id = UUID.randomUUID().toString();

    assertDeadLetteredWithoutRetry("UnknownMessage", "{\"id\":\"" + id + "\"}", id);
  }

  @Test
  public void messageWithBrokenJson_IsDeadLetteredWithoutRetry_AndNextMessageIsHandled() {
    var id = UUID.randomUUID().toString();

    assertDeadLetteredWithoutRetry(PaymentReceived.class.getName(), "{\"id\":\"" + id + "\",", id);
  }

  private void assertDeadLetteredWithoutRetry(String messageType, String payload, String id) {
    var deadLetteredBefore = deadLetteredCount(messageType);
    var healthy = new PaymentReceived(UUID.randomUUID().toString());
    var handled = new CopyOnWriteArrayList<String>();
    messageBus.subscribe(PaymentReceived.class, message -> handled.add(message.id()));

    sendRaw(messageType, payload);
    messageBus.publish(List.of(healthy));

    var deadLettered = await().atMost(Duration.ofSeconds(30))
      .until(() -> findOnDeadLetterTopic(id), Optional::isPresent)
      .get();

    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> assertThat(handled).contains(healthy.id()));

    assertThat(deadLettered.value()).isEqualTo(payload);
    assertThat(deliveryAttempt(deadLettered))
      .as("unreadable message should not be retried")
      .isEqualTo(1);
    assertThat(exceptionCause(deadLettered)).isEqualTo(UnreadableMessageException.class.getName());
    assertThat(deadLetteredCount(messageType)).isEqualTo(deadLetteredBefore + 1);
    assertThat(failureStrategy.failures).containsExactly(new Failure(messageType, Optional.empty()));
    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> assertThat(failedSpans()).hasSize(1));
  }

  private double deadLetteredCount(String messageType) {
    var counter = meterRegistry.find("messaging.dead_lettered").tag("message.type", messageType).counter();
    return counter != null ? counter.count() : 0;
  }

  private List<SpanData> failedSpans() {
    return spanExporter.getFinishedSpanItems().stream()
      .filter(span -> span.getStatus().getStatusCode() == StatusCode.ERROR)
      .toList();
  }

  private void sendRaw(String messageType, String payload) {
    var factory = new DefaultKafkaProducerFactory<>(
      producerFactory.getConfigurationProperties(),
      new StringSerializer(),
      new StringSerializer()
    );
    try {
      var record = new ProducerRecord<String, String>(topic, payload);
      record.headers().add(DefaultJacksonJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME, messageType.getBytes());
      new KafkaTemplate<>(factory).send(record).join();
    } finally {
      factory.destroy();
    }
  }

  private static int deliveryAttempt(ConsumerRecord<String, String> record) {
    return ByteBuffer.wrap(record.headers().lastHeader(KafkaHeaders.DELIVERY_ATTEMPT).value()).getInt();
  }

  private static String exceptionCause(ConsumerRecord<String, String> record) {
    return new String(record.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN).value());
  }

  private Optional<ConsumerRecord<String, String>> findOnDeadLetterTopic(String messageId) {
    var overrides = new Properties();
    overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

    var rawConsumerFactory = new DefaultKafkaConsumerFactory<>(
      consumerFactory.getConfigurationProperties(),
      new StringDeserializer(),
      new StringDeserializer()
    );

    try (var consumer = rawConsumerFactory.createConsumer("dlt-reader-" + UUID.randomUUID(), null, null, overrides)) {
      consumer.subscribe(List.of(topic + "-dlt"));

      for (var record : consumer.poll(Duration.ofSeconds(2))) {
        if (record.value().contains(messageId))
          return Optional.of(record);
      }
      return Optional.empty();
    }
  }
}
