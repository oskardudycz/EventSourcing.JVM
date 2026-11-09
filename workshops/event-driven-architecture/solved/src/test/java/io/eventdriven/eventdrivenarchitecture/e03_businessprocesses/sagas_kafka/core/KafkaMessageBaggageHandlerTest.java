package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.micrometer.KafkaRecordReceiverContext;

import java.util.Collections;
import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.*;
import static org.assertj.core.api.Assertions.assertThat;

public class KafkaMessageBaggageHandlerTest {
  private final Tracer tracer = tracer();
  private final KafkaMessageBaggageHandler handler = new KafkaMessageBaggageHandler(tracer);

  @Test
  public void handlingAMessage_MakesItsMessageIdTheCausationId() {
    var record = new ConsumerRecord<String, Object>("topic", 0, 0, "key", "message");
    record.headers().add(MESSAGE_ID, "handled-message-id".getBytes());
    var context = new KafkaRecordReceiverContext(record, "listener", () -> "cluster");

    handler.onScopeOpened(context);
    var causationId = tracer.getBaggage(CAUSATION_ID).get();
    handler.onScopeClosed(context);

    assertThat(causationId).isEqualTo("handled-message-id");
    assertThat(tracer.getBaggage(CAUSATION_ID).get()).as("causationId should end with the handling").isNull();
  }

  @Test
  public void handlingAMessageWithoutMessageId_SetsNoCausationId() {
    var record = new ConsumerRecord<String, Object>("topic", 0, 0, "key", "message");
    var context = new KafkaRecordReceiverContext(record, "listener", () -> "cluster");

    handler.onScopeOpened(context);
    var causationId = tracer.getBaggage(CAUSATION_ID).get();
    handler.onScopeClosed(context);

    assertThat(causationId).isNull();
  }

  private static Tracer tracer() {
    var openTelemetry = OpenTelemetrySdk.builder().build();
    var currentTraceContext = new OtelCurrentTraceContext();

    return new OtelTracer(
      openTelemetry.getTracer("test-tracer"),
      currentTraceContext,
      event -> {
      },
      new OtelBaggageManager(
        currentTraceContext,
        List.of(CORRELATION_ID, CAUSATION_ID),
        Collections.emptyList()
      )
    );
  }
}
