package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.*;
import static org.assertj.core.api.Assertions.assertThat;

public class MessageMetadataTest {
  private final Tracer tracer = tracer();

  @Test
  public void messageStartingAProcess_GetsAMessageIdAndAGeneratedCorrelationId() {
    var record = new ProducerRecord<String, Object>("topic", "message");

    setMetadataHeaders(record, tracer);

    assertThat(header(record, MESSAGE_ID)).satisfies(UUID::fromString);
    assertThat(header(record, CORRELATION_ID)).satisfies(UUID::fromString);
  }

  @Test
  public void messagePublishedWithinAProcess_KeepsItsCorrelationId() {
    var record = new ProducerRecord<String, Object>("topic", "message");

    try (var _ = tracer.createBaggageInScope(CORRELATION_ID, "process-correlation-id")) {
      setMetadataHeaders(record, tracer);
    }

    assertThat(header(record, CORRELATION_ID)).isEqualTo("process-correlation-id");
  }

  @Test
  public void messageType_IsReadFromTheTypeHeader() {
    var record = new ConsumerRecord<String, Object>("topic", 0, 0, "key", "message");
    record.headers().add(DefaultJacksonJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME, "RoomInspected".getBytes());

    assertThat(getMessageType(record)).isEqualTo("RoomInspected");
  }

  @Test
  public void recordWithoutTypeHeader_HasNoMessageType() {
    assertThat(getMessageType(new ConsumerRecord<String, Object>("topic", 0, 0, "key", "message"))).isNull();
  }

  private static String header(ProducerRecord<?, ?> record, String name) {
    var header = record.headers().lastHeader(name);
    assertThat(header).as(name + " header should be set").isNotNull();
    return new String(header.value());
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
