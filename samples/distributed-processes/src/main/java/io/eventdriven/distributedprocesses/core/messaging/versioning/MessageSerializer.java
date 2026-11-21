package io.eventdriven.distributedprocesses.core.messaging.versioning;

import io.eventdriven.distributedprocesses.core.messaging.UnreadableMessageException;
import tools.jackson.databind.ObjectMapper;

public class MessageSerializer {
    private final MessageMapping mapping;
    private final ObjectMapper objectMapper;
    private final String trustedPackage;

    public MessageSerializer(MessageMapping mapping, ObjectMapper objectMapper, String trustedPackage) {
        this.mapping = mapping;
        this.objectMapper = objectMapper;
        this.trustedPackage = trustedPackage;
    }

    public String typeNameOf(Class<?> messageClass) {
        return mapping.typeNameOf(messageClass)
                .orElseThrow(() -> new IllegalStateException(
                        "The message type %s has no stable type name".formatted(messageClass.getName())));
    }

    public boolean isRegistered(String typeName) {
        return mapping.classOf(typeName).isPresent()
                || mapping.transformerOf(typeName).isPresent();
    }

    public byte[] write(Object message) {
        return objectMapper.writeValueAsBytes(message);
    }

    public Object read(String typeName, byte[] body) {
        try {
            var transformer = mapping.transformerOf(typeName);

            return transformer.isPresent()
                    ? transformer.get().transform(body, objectMapper)
                    : objectMapper.readValue(body, classOf(typeName));
        } catch (UnreadableMessageException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new UnreadableMessageException(typeName, e);
        }
    }

    private Class<?> classOf(String typeName) {
        return mapping.classOf(typeName).orElseGet(() -> trustedClassOf(typeName));
    }

    private Class<?> trustedClassOf(String typeName) {
        if (!typeName.startsWith(trustedPackage + "."))
            throw new UnreadableMessageException(typeName, "the type is not trusted");

        try {
            return Class.forName(typeName, false, getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new UnreadableMessageException(typeName, e);
        }
    }
}
