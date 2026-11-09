package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.DefaultClassMapper;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata.*;
import static org.assertj.core.api.Assertions.assertThat;

public class MessageMetadataTest {
  private final Tracer tracer = tracer();

  @Test
  public void messageStartingAProcess_GetsAMessageIdAndAGeneratedCorrelationId() {
    var message = new Message(new byte[0], new MessageProperties());

    setMetadataHeaders(message, tracer);

    assertThat(message.getMessageProperties().<String>getHeader(MESSAGE_ID)).satisfies(UUID::fromString);
    assertThat(message.getMessageProperties().<String>getHeader(CORRELATION_ID)).satisfies(UUID::fromString);
  }

  @Test
  public void messagePublishedWithinAProcess_KeepsItsCorrelationId() {
    var message = new Message(new byte[0], new MessageProperties());

    try (var _ = tracer.createBaggageInScope(CORRELATION_ID, "process-correlation-id")) {
      setMetadataHeaders(message, tracer);
    }

    assertThat(message.getMessageProperties().<String>getHeader(CORRELATION_ID)).isEqualTo("process-correlation-id");
  }

  @Test
  public void messageType_IsReadFromTheTypeHeader() {
    var properties = new MessageProperties();
    properties.setHeader(DefaultClassMapper.DEFAULT_CLASSID_FIELD_NAME, "RoomInspected");

    assertThat(getMessageType(new Message(new byte[0], properties))).isEqualTo("RoomInspected");
  }

  @Test
  public void messageWithoutTypeHeader_HasNoMessageType() {
    assertThat(getMessageType(new Message(new byte[0], new MessageProperties()))).isNull();
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
