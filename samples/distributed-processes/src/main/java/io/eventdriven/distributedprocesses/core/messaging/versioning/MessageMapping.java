package io.eventdriven.distributedprocesses.core.messaging.versioning;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

public class MessageMapping {
    private final Map<String, Class<?>> classesByName = new HashMap<>();
    private final Map<Class<?>, String> namesByClass = new HashMap<>();
    private final Map<String, MessageTransformer> transformersByName = new HashMap<>();

    public MessageMapping register(String typeName, Class<?> messageClass) {
        classesByName.put(typeName, messageClass);
        namesByClass.put(messageClass, typeName);
        return this;
    }

    public <OldMessage, Message> MessageMapping register(
            String oldTypeName, Class<OldMessage> oldMessageClass, Function<OldMessage, Message> transform) {
        transformersByName.put(
                oldTypeName, (json, objectMapper) -> transform.apply(objectMapper.readValue(json, oldMessageClass)));
        return this;
    }

    public <Message> MessageMapping register(String oldTypeName, Function<JsonNode, Message> transformJson) {
        transformersByName.put(oldTypeName, (json, objectMapper) -> transformJson.apply(objectMapper.readTree(json)));
        return this;
    }

    public Optional<String> typeNameOf(Class<?> messageClass) {
        return Optional.ofNullable(namesByClass.get(messageClass));
    }

    public Optional<Class<?>> classOf(String typeName) {
        return Optional.ofNullable(classesByName.get(typeName));
    }

    public Optional<MessageTransformer> transformerOf(String typeName) {
        return Optional.ofNullable(transformersByName.get(typeName));
    }
}
