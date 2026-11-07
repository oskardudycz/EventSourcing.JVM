package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.TestApplicationConfig;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.KafkaException;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = TestApplicationConfig.class)
@ComponentScan(basePackages = {
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core",
  "io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core"
})
@TestPropertySource(properties = {
  "messaging.type=kafka",
  "app.kafka.topic=publish-failure",
  "app.kafka.groupId=publish-failure",
  "app.kafka.publish-timeout=5s",
  "spring.kafka.producer.properties.max.request.size=1024"
})
@Import(KafkaTestConfiguration.class)
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension.class)
public class KafkaPublishFailureTest {
  public record RoomInspected(String id, String notes) {
  }

  private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(5);

  @Autowired
  private KafkaMessageBus messageBus;

  @Test
  public void publishingAMessageTheBrokerRejects_ThrowsTheKafkaFailure(CapturedOutput output) {
    var tooLarge = new RoomInspected(UUID.randomUUID().toString(), "x".repeat(2048));
    var startedAt = System.nanoTime();

    assertThatThrownBy(() -> messageBus.publish(List.of(tooLarge)))
      .isInstanceOf(KafkaException.class)
      .hasCauseInstanceOf(RecordTooLargeException.class);

    assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(PUBLISH_TIMEOUT);
    assertThat(output).contains("Exception thrown when sending a message with key='null' and payload='RoomInspected[id=" + tooLarge.id());
  }

  @Test
  public void publishingWhenInterrupted_ThrowsAndKeepsTheInterruptFlag() {
    var message = new RoomInspected(UUID.randomUUID().toString(), "clean");
    messageBus.publish(List.of(message));

    try {
      Thread.currentThread().interrupt();

      assertThatThrownBy(() -> messageBus.publish(List.of(message)));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }
}
