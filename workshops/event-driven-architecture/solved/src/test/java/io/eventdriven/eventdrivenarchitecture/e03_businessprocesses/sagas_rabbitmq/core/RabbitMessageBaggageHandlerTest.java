package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.otel.bridge.OtelBaggageManager;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.micrometer.RabbitMessageReceiverContext;

import java.util.Collections;
import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages.MessageMetadata.*;
import static org.assertj.core.api.Assertions.assertThat;

public class RabbitMessageBaggageHandlerTest {
  private final Tracer tracer = tracer();
  private final RabbitMessageBaggageHandler handler = new RabbitMessageBaggageHandler(tracer);

  @Test
  public void handlingAMessage_MakesItsMessageIdTheCausationId() {
    var properties = new MessageProperties();
    properties.setHeader(MESSAGE_ID, "handled-message-id");
    var context = new RabbitMessageReceiverContext(new Message(new byte[0], properties), "listener");

    handler.onScopeOpened(context);
    var causationId = tracer.getBaggage(CAUSATION_ID).get();
    handler.onScopeClosed(context);

    assertThat(causationId).isEqualTo("handled-message-id");
    assertThat(tracer.getBaggage(CAUSATION_ID).get()).as("causationId should end with the handling").isNull();
  }

  @Test
  public void handlingAMessageWithoutMessageId_SetsNoCausationId() {
    var context = new RabbitMessageReceiverContext(new Message(new byte[0], new MessageProperties()), "listener");

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
