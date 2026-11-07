package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core;

import tools.jackson.databind.ObjectMapper;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.UnreadableMessageException;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning.MessageTransformations;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.MessageConverter;

public class VersionedMessageConverter {
  private final ObjectMapper objectMapper;
  private final MessageTransformations transformations;
  private final MessageConverter messageConverter;

  public VersionedMessageConverter(
    ObjectMapper objectMapper,
    MessageTransformations transformations,
    MessageConverter messageConverter
  ) {
    this.objectMapper = objectMapper;
    this.transformations = transformations;
    this.messageConverter = messageConverter;
  }

  public Object read(Message message) {
    String messageType = getMessageTypeHeader(message);
    byte[] jsonBytes = message.getBody();

    try {
      // First: Try transformations (for old message schemas)
      var transformed = transformations.tryTransform(objectMapper, messageType, jsonBytes);
      if (transformed.isPresent()) {
        return transformed.get();
      }

      // Then: Normal deserialization using JacksonJsonMessageConverter
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
