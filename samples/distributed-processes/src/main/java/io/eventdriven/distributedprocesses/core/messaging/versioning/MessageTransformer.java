package io.eventdriven.distributedprocesses.core.messaging.versioning;

import tools.jackson.databind.ObjectMapper;

@FunctionalInterface
public interface MessageTransformer {
    Object transform(byte[] json, ObjectMapper objectMapper);
}
