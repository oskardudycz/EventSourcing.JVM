package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.util.Collections;
import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata.*;
import static org.assertj.core.api.Assertions.assertThat;

public class RabbitMessageBaggageHandlerTest {
  private final Tracer tracer = tracer();

  @Test
  public void publishingWhileHandlingAMessage_SetsCausationIdToTheHandledMessageId() {
    var message = new Message(new byte[0], new MessageProperties());

    try (var _ = tracer.createBaggageInScope(CAUSATION_ID, "handled-message-id");
         var _ = tracer.createBaggageInScope(ENTITY_ID, "entity-id")) {
      RabbitMessageBaggageHandler.setHeadersFromBaggage(message, tracer);
    }

    assertThat(message.getMessageProperties().<String>getHeader(CAUSATION_ID))
      .as("causationId header should be set")
      .isEqualTo("handled-message-id");
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
