package io.eventdriven.distributedprocesses.core.messaging.versioning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;

public class MessageMapping {
    record OldName(String typeName, Class<?> currentClass, MessageTransformer transformer) {}

    private final List<String> trustedPackages = new ArrayList<>();
    private final Map<String, Class<?>> explicitNames = new LinkedHashMap<>();
    private final List<OldName> oldNames = new ArrayList<>();

    public MessageMapping trust(String... packageNames) {
        trustedPackages.addAll(Arrays.asList(packageNames));
        return this;
    }

    public MessageMapping register(String typeName, Class<?> messageClass) {
        explicitNames.put(typeName, messageClass);
        return this;
    }

    public <OldMessage, Message> MessageMapping register(
            String oldTypeName,
            Class<OldMessage> oldMessageClass,
            Class<Message> messageClass,
            Function<OldMessage, Message> transform) {
        oldNames.add(new OldName(
                oldTypeName,
                messageClass,
                (json, jsonMapper) -> transform.apply(jsonMapper.readValue(json, oldMessageClass))));
        return this;
    }

    public <Message> MessageMapping register(
            String oldTypeName, Class<Message> messageClass, Function<JsonNode, Message> transformJson) {
        oldNames.add(new OldName(
                oldTypeName, messageClass, (json, jsonMapper) -> transformJson.apply(jsonMapper.readTree(json))));
        return this;
    }

    List<String> trustedPackages() {
        return trustedPackages;
    }

    Map<String, Class<?>> explicitNames() {
        return explicitNames;
    }

    List<OldName> oldNames() {
        return oldNames;
    }
}
