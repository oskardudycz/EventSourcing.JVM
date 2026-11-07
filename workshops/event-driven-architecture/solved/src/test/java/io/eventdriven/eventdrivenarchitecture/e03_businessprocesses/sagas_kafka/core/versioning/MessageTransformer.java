package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.versioning;

import tools.jackson.databind.ObjectMapper;

@FunctionalInterface
public interface MessageTransformer {
  Object transform(byte[] json, ObjectMapper objectMapper);
}
