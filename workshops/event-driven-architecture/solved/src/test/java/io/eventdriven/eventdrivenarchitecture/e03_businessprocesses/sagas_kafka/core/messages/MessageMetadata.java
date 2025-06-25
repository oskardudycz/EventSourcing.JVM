package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages;

import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper;
import org.springframework.messaging.MessageHeaders;

import java.util.Optional;
import java.util.UUID;

public abstract class MessageMetadata {
  public static final String MESSAGE_ID = "messageId";
  public static final String CORRELATION_ID = "correlationId";
  public static final String CAUSATION_ID = "causationId";
  public static final String ENTITY_ID = "entityId";
  public static final String ENTITY_VERSION = "entityVersion";

  public static UUID getMessageId(MessageHeaders headers) {
    return headers.get(MessageHeaders.ID, UUID.class);
  }

  public static Optional<String> getMessageType(MessageHeaders headers) {
    return Optional.ofNullable(headers.get(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME, String.class));
  }

  public static String getMessageType(ConsumerRecord<String, String> record) {
    var header = record.headers().lastHeader(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME);
    return header != null ? new String(header.value()) : null;
  }

  public static Optional<String> getCorrelationId(MessageHeaders headers) {
    return Optional.ofNullable(headers.get(CORRELATION_ID, String.class));
  }

  public static Optional<String> getCausationId(MessageHeaders headers) {
    return Optional.ofNullable(headers.get(CAUSATION_ID, String.class));
  }

  public static Optional<String> getEntityId(MessageHeaders headers) {
    return Optional.ofNullable(headers.get(ENTITY_ID, String.class));
  }

  public static Optional<Long> getEntityVersion(MessageHeaders headers) {
    return Optional.ofNullable(headers.get(ENTITY_VERSION, Long.class));
  }

  public static Optional<Long> getOffset(MessageHeaders headers) {
    return Optional.ofNullable(headers.get(KafkaHeaders.OFFSET, Long.class));
  }

  public static Optional<Integer> getPartition(MessageHeaders headers) {
    return Optional.ofNullable(headers.get(KafkaHeaders.RECEIVED_PARTITION, Integer.class));
  }

  public static Optional<Long> getTimestamp(MessageHeaders headers) {
    return Optional.ofNullable(headers.get(KafkaHeaders.RECEIVED_TIMESTAMP, Long.class));
  }

  public static Optional<String> getCorrelationIdFromBaggage(Tracer tracer) {
    var baggage = tracer.getBaggage(CORRELATION_ID);
    return baggage != null ? Optional.ofNullable(baggage.get()) : Optional.empty();
  }

  public static Optional<String> getCausationIdFromBaggage(Tracer tracer) {
    var baggage = tracer.getBaggage(CAUSATION_ID);
    return baggage != null ? Optional.ofNullable(baggage.get()) : Optional.empty();
  }

  public static Optional<String> getEntityIdFromBaggage(Tracer tracer) {
    var baggage = tracer.getBaggage(ENTITY_ID);
    return baggage != null ? Optional.ofNullable(baggage.get()) : Optional.empty();
  }

  public static Optional<Long> getEntityVersionFromBaggage(Tracer tracer) {
    var baggage = tracer.getBaggage(ENTITY_VERSION);
    if (baggage == null) return Optional.empty();
    String value = baggage.get();
    try {
      return value != null ? Optional.of(Long.parseLong(value)) : Optional.empty();
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }
}
