package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.*;
import static org.assertj.core.api.Assertions.assertThat;

public class KafkaMessageBaggageHandlerTest {
  private final Tracer tracer = tracer();

  @Test
  public void publishingWhileHandlingAMessage_SetsCausationIdToTheHandledMessageId() {
    var record = new ProducerRecord<String, Object>("topic", "message");

    try (var _ = tracer.createBaggageInScope(CAUSATION_ID, "handled-message-id");
         var _ = tracer.createBaggageInScope(ENTITY_ID, "entity-id")) {
      KafkaMessageBaggageHandler.setHeadersFromBaggage(record, tracer);
    }

    var causationId = record.headers().lastHeader(CAUSATION_ID);
    assertThat(causationId).as("causationId header should be set").isNotNull();
    assertThat(new String(causationId.value())).isEqualTo("handled-message-id");
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
        List.of(MESSAGE_ID, CORRELATION_ID, CAUSATION_ID, ENTITY_ID, ENTITY_VERSION),
        Collections.emptyList()
      )
    );
  }
}
