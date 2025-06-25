package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.versioning;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

@FunctionalInterface
public interface MessageTransformer {
  Optional<Object> transform(byte[] json, ObjectMapper objectMapper);
}
