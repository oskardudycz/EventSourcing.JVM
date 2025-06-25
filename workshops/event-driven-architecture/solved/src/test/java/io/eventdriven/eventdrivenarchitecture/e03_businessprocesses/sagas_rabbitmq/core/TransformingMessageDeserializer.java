package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning.MessageTransformations;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TransformingMessageDeserializer {
  private final ObjectMapper objectMapper;
  private final MessageTransformations transformations;
  private final Jackson2JsonMessageConverter messageConverter;

  public TransformingMessageDeserializer(
    ObjectMapper objectMapper,
    MessageTransformations transformations,
    Jackson2JsonMessageConverter messageConverter
  ) {
    this.objectMapper = objectMapper;
    this.transformations = transformations;
    this.messageConverter = messageConverter;
  }

  public Optional<Object> deserialize(Message message) {
    String messageType = getMessageTypeHeader(message);
    byte[] jsonBytes = message.getBody();

    // First: Try transformations (for old message schemas)
    var transformed = transformations.tryTransform(objectMapper, messageType, jsonBytes);
    if (transformed.isPresent()) {
      return transformed;
    }

    // Then: Normal deserialization using Jackson2JsonMessageConverter
    try {
      Object result = messageConverter.fromMessage(message);
      return Optional.ofNullable(result);
    } catch (Exception e) {
      System.err.println("Failed to deserialize message of type " + messageType + ": " + e.getMessage());
      return Optional.empty();
    }
  }

  private String getMessageTypeHeader(Message message) {
    Object typeHeader = message.getMessageProperties().getHeaders().get("__TypeId__");
    if (typeHeader == null) {
      typeHeader = message.getMessageProperties().getHeaders().get("eventType");
    }
    return typeHeader != null ? typeHeader.toString() : null;
  }
}