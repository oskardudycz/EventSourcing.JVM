package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.versioning.MessageTransformations;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.messages.MessageMetadata.getMessageType;

@Component
public class TransformingMessageDeserializer {
  private final JsonDeserializer<Object> kafkaDeserializer;
  private final ObjectMapper objectMapper;
  private final MessageTransformations transformations;

  public TransformingMessageDeserializer(
    JsonDeserializer<Object> kafkaDeserializer,
    ObjectMapper objectMapper,
    MessageTransformations transformations
  ) {
    this.kafkaDeserializer = kafkaDeserializer;
    this.objectMapper = objectMapper;
    this.transformations = transformations;
  }

  public Optional<Object> deserialize(ConsumerRecord<String, String> record) {
    String messageType = getMessageType(record);
    byte[] jsonBytes = record.value().getBytes();

    var transformed = transformations.tryTransform(objectMapper, messageType, jsonBytes);
    if (transformed.isPresent())
      return transformed;

    try {
      Object result = kafkaDeserializer.deserialize(record.topic(), record.headers(), jsonBytes);
      return Optional.ofNullable(result);
    } catch (Exception e) {
      System.out.println("Unable to deserialize message:" + messageType + e.getMessage());
      return Optional.empty();
    }
  }
}
