package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.InMemorySpansConfiguration;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RecordingFailureStrategy;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.RecordingFailureStrategy.Failure;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = RabbitMQSagaTestApplication.class)
@TestPropertySource(properties = {
  "app.rabbitmq.exchange=error-handling",
  "app.messaging.retry.initial-interval=10ms"
})
@Import({
  InMemorySpansConfiguration.class,
  RecordingFailureStrategy.Config.class
})
@AutoConfigureTracing
@AutoConfigureMetrics
public class RabbitMQErrorHandlingTest {
  public record PaymentReceived(String id) {
  }

  @Autowired
  private RabbitMQMessageBus messageBus;

  @Autowired
  private RecordingFailureStrategy failureStrategy;

  @Autowired
  private MeterRegistry meterRegistry;

  @Autowired
  private InMemorySpanExporter spanExporter;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Value("${app.rabbitmq.exchange}")
  private String exchange;

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
      .until(() -> findOnDeadLetterQueue(poison.id()), Objects::nonNull);

    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> assertThat(attempts).contains(healthy.id()));

    assertThat(attempts.stream().filter(poison.id()::equals))
      .as("poison message should be attempted 3 times, then dead-lettered")
      .hasSize(3);
    assertThat(deadLettered.getMessageProperties().<Object>getHeader("x-death"))
      .as("RabbitMQ records why and from where the message was dead-lettered")
      .isNotNull();
    assertThat(deadLetteredCount(PaymentReceived.class.getName())).isEqualTo(deadLetteredBefore + 1);
    assertThat(failureStrategy.failures)
      .as("the strategy should see the message it decides about")
      .contains(new Failure(PaymentReceived.class.getName(), Optional.of(poison)));
    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> assertThat(failedSpans())
        .as("retries run inside one delivery, so RabbitMQ shows a single failed receive")
        .hasSize(1));
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

    rabbitTemplate.send(exchange, "raw", MessageBuilder.withBody(payload.getBytes())
      .setContentType(MessageProperties.CONTENT_TYPE_JSON)
      .setHeader(DefaultClassMapper.DEFAULT_CLASSID_FIELD_NAME, messageType)
      .build());
    messageBus.publish(List.of(healthy));

    var deadLettered = await().atMost(Duration.ofSeconds(30))
      .until(() -> findOnDeadLetterQueue(id), Objects::nonNull);

    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> assertThat(handled).contains(healthy.id()));

    assertThat(new String(deadLettered.getBody())).isEqualTo(payload);
    assertThat(failureStrategy.failures)
      .as("unreadable message should fail once, without retry")
      .containsExactly(new Failure(messageType, Optional.empty()));
    assertThat(deadLetteredCount(messageType)).isEqualTo(deadLetteredBefore + 1);
    await().atMost(Duration.ofSeconds(10))
      .untilAsserted(() -> assertThat(failedSpans())
        .singleElement()
        .satisfies(span -> assertThat(span.getEvents())
          .anySatisfy(event -> assertThat(event.getAttributes().asMap().values())
            .anySatisfy(value -> assertThat(value.toString()).contains(UnreadableMessageException.class.getName())))));
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

  private Message findOnDeadLetterQueue(String id) {
    var message = rabbitTemplate.receive(exchange + "-saga-dlq", 1000);
    return message != null && new String(message.getBody()).contains(id) ? message : null;
  }
}
