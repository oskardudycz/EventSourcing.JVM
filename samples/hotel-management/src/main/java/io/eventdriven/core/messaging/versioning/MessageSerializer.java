package io.eventdriven.core.messaging.versioning;

import io.eventdriven.core.messaging.UnreadableMessageException;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public class MessageSerializer {
    private final MessageTypeMapper typeMapper;
    private final JsonMapper jsonMapper;

    public MessageSerializer(MessageTypeMapper typeMapper, JsonMapper jsonMapper) {
        this.typeMapper = typeMapper;
        this.jsonMapper = jsonMapper;
    }

    public static JsonMapper newJsonMapper() {
        return JsonMapper.builder()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
                .build();
    }

    public MessageTypeMapper typeMapper() {
        return typeMapper;
    }

    public JsonMapper jsonMapper() {
        return jsonMapper;
    }

    public String typeNameOf(Class<?> messageClass) {
        return typeMapper.nameOf(messageClass);
    }

    public Set<String> typeNamesOf(Class<?> messageClass) {
        return typeMapper.namesOf(messageClass);
    }

    public boolean isKnown(String typeName) {
        return typeMapper.classOf(typeName).isPresent();
    }

    public byte[] write(Object message) {
        return jsonMapper.writeValueAsBytes(message);
    }

    public Optional<Object> tryTransform(String typeName, byte[] body) {
        return typeMapper.transformerOf(typeName).map(transformer -> transformer.transform(body, jsonMapper));
    }

    public Object read(String typeName, byte[] body) {
        try {
            var transformed = tryTransform(typeName, body);

            return transformed.isPresent() ? transformed.get() : jsonMapper.readValue(body, classOf(typeName));
        } catch (UnreadableMessageException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new UnreadableMessageException(typeName, e);
        }
    }

    private Class<?> classOf(String typeName) {
        return typeMapper
                .classOf(typeName)
                .orElseThrow(() -> new UnreadableMessageException(
                        typeName, "no class is mapped to this type name, or its package is not trusted"));
    }
}
