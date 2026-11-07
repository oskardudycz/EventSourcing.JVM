package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import tools.jackson.databind.ObjectMapper;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.versioning.MessageTransformations;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.stereotype.Component;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.getMessageType;

@Component
public class TransformingMessageDeserializer {
  private final JacksonJsonDeserializer<Object> kafkaDeserializer;
  private final ObjectMapper objectMapper;
  private final MessageTransformations transformations;

  public TransformingMessageDeserializer(
    JacksonJsonDeserializer<Object> kafkaDeserializer,
    ObjectMapper objectMapper,
    MessageTransformations transformations
  ) {
    this.kafkaDeserializer = kafkaDeserializer;
    this.objectMapper = objectMapper;
    this.transformations = transformations;
  }

  public Object deserialize(ConsumerRecord<String, String> record) {
    String messageType = getMessageType(record);
    byte[] jsonBytes = record.value().getBytes();

    var transformed = transformations.tryTransform(objectMapper, messageType, jsonBytes);
    if (transformed.isPresent())
      return transformed.get();

    try {
      return kafkaDeserializer.deserialize(record.topic(), record.headers(), jsonBytes);
    } catch (Exception e) {
      throw new UnreadableMessageException(messageType, e);
    }
  }
}
