package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.versioning;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Optional;
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
      (json, objectMapper) -> {
        try {
          Old old = objectMapper.readValue(json, oldClass);
          return Optional.of(transform.apply(old));
        } catch (IOException e) {
          return Optional.empty();
        }
      }
    );
  }

  public static <New> MessageTransformation from(
    String messageTypeName,
    Function<JsonNode, New> transform
  ) {
    return new MessageTransformation(
      messageTypeName,
      (json, objectMapper) -> {
        try {
          JsonNode node = objectMapper.readTree(json);
          return Optional.of(transform.apply(node));
        } catch (IOException e) {
          return Optional.empty();
        }
      }
    );
  }
}
