package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages;

import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.kafka.support.mapping.DefaultJacksonJavaTypeMapper;

import java.util.Optional;
import java.util.UUID;

public final class MessageMetadata {
  public static final String MESSAGE_ID = "messageId";
  public static final String CORRELATION_ID = "correlationId";
  public static final String CAUSATION_ID = "causationId";

  private MessageMetadata() {
  }

  public static @Nullable String getMessageType(ConsumerRecord<?, ?> record) {
    var header = record.headers().lastHeader(DefaultJacksonJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME);
    return header != null ? new String(header.value()) : null;
  }

  public static void setMetadataHeaders(ProducerRecord<?, ?> record, Tracer tracer) {
    var correlationId = getCorrelationIdFromBaggage(tracer)
      .orElse(UUID.randomUUID().toString());
    record.headers().add(CORRELATION_ID, correlationId.getBytes());

    record.headers().add(MESSAGE_ID, UUID.randomUUID().toString().getBytes());
  }

  private static Optional<String> getCorrelationIdFromBaggage(Tracer tracer) {
    var baggage = tracer.getBaggage(CORRELATION_ID);
    return baggage != null ? Optional.ofNullable(baggage.get()) : Optional.empty();
  }
}
