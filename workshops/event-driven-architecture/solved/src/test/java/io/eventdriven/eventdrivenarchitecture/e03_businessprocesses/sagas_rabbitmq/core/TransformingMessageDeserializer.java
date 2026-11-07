package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import tools.jackson.databind.ObjectMapper;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning.MessageTransformations;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.stereotype.Component;

@Component
public class TransformingMessageDeserializer {
  private final ObjectMapper objectMapper;
  private final MessageTransformations transformations;
  private final JacksonJsonMessageConverter messageConverter;

  public TransformingMessageDeserializer(
    ObjectMapper objectMapper,
    MessageTransformations transformations,
    JacksonJsonMessageConverter messageConverter
  ) {
    this.objectMapper = objectMapper;
    this.transformations = transformations;
    this.messageConverter = messageConverter;
  }

  public Object deserialize(Message message) {
    String messageType = getMessageTypeHeader(message);
    byte[] jsonBytes = message.getBody();

    // First: Try transformations (for old message schemas)
    var transformed = transformations.tryTransform(objectMapper, messageType, jsonBytes);
    if (transformed.isPresent()) {
      return transformed.get();
    }

    // Then: Normal deserialization using JacksonJsonMessageConverter
    try {
      return messageConverter.fromMessage(message);
    } catch (Exception e) {
      throw new UnreadableMessageException(messageType, e);
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