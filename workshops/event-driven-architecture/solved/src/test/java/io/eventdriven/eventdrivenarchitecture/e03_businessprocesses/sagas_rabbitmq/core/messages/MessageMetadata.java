package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.messages;

import io.micrometer.tracing.Tracer;
import org.jspecify.annotations.Nullable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.DefaultClassMapper;

import java.util.Optional;
import java.util.UUID;

public final class MessageMetadata {
  public static final String MESSAGE_ID = "messageId";
  public static final String CORRELATION_ID = "correlationId";
  public static final String CAUSATION_ID = "causationId";

  private MessageMetadata() {
  }

  public static @Nullable String getMessageType(Message message) {
    return message.getMessageProperties().getHeader(DefaultClassMapper.DEFAULT_CLASSID_FIELD_NAME);
  }

  public static void setMetadataHeaders(Message message, Tracer tracer) {
    var properties = message.getMessageProperties();

    var correlationId = getCorrelationIdFromBaggage(tracer)
      .orElse(UUID.randomUUID().toString());
    properties.setHeader(CORRELATION_ID, correlationId);

    properties.setHeader(MESSAGE_ID, UUID.randomUUID().toString());
  }

  private static Optional<String> getCorrelationIdFromBaggage(Tracer tracer) {
    var baggage = tracer.getBaggage(CORRELATION_ID);
    return baggage != null ? Optional.ofNullable(baggage.get()) : Optional.empty();
  }
}
