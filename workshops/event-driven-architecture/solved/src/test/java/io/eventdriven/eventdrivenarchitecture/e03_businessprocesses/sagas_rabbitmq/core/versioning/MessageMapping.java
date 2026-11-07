package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.versioning;

import tools.jackson.databind.JsonNode;

import java.util.*;
import java.util.function.Function;

public class MessageMapping {
  private final Map<String, Class<?>> typeMappings = new HashMap<>();
  private final List<MessageTransformation> transformations = new ArrayList<>();

  public <T> MessageMapping register(String typeName, Class<T> clazz) {
    typeMappings.put(typeName, clazz);
    return this;
  }

  public <Old, New> MessageMapping register(
    String oldTypeName,
    Class<Old> oldClass,
    Function<Old, New> transform
  ) {
    transformations.add(MessageTransformation.from(oldClass, oldTypeName, transform));
    return this;
  }

  public <New> MessageMapping register(
    String oldTypeName,
    Function<JsonNode, New> transform
  ) {
    transformations.add(MessageTransformation.from(oldTypeName, transform));
    return this;
  }

  Map<String, Class<?>> getTypeMappings() {
    return typeMappings;
  }

  List<MessageTransformation> getTransformations() {
    return transformations;
  }
}
