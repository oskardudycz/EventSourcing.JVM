package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class MessageTransformations {
  private final Map<String, MessageTransformer> transformations = new HashMap<>();

  public MessageTransformations(List<MessageTransformation> messageTransformations) {
    for (var transformation : messageTransformations) {
      register(transformation);
    }
  }

  public Optional<Object> tryTransform(ObjectMapper objectMapper, String messageTypeName, byte[] json) {
    return Optional
      .ofNullable(transformations.get(messageTypeName))
      .map(t -> t.transform(json, objectMapper));
  }

  public MessageTransformations register(MessageTransformation transformation) {
    transformations.put(transformation.messageTypeName(), transformation.transformer());

    return this;
  }

  public <Message> MessageTransformations register(
    String messageTypeName,
    Function<JsonNode, Message> transformJson
  ) {
    return register(MessageTransformation.from(messageTypeName, transformJson));
  }

  public <OldMessage, Message> MessageTransformations register(
    Class<OldMessage> oldMessageClass,
    String messageTypeName,
    Function<OldMessage, Message> transformMessage
  ) {
    return register(MessageTransformation.from(oldMessageClass, messageTypeName, transformMessage));
  }
}
