package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.versioning;

import tools.jackson.databind.JsonNode;

import java.util.function.Function;

public record MessageTransformation(
  String messageTypeName,
  MessageTransformer transformer
) {

  public static <Old, New> MessageTransformation from(
    Class<Old> oldClass,
    String messageTypeName,
    Function<Old, New> transform
  ) {
    return new MessageTransformation(
      messageTypeName,
      (json, objectMapper) -> transform.apply(objectMapper.readValue(json, oldClass))
    );
  }

  public static <New> MessageTransformation from(
    String messageTypeName,
    Function<JsonNode, New> transform
  ) {
    return new MessageTransformation(
      messageTypeName,
      (json, objectMapper) -> transform.apply(objectMapper.readTree(json))
    );
  }
}
