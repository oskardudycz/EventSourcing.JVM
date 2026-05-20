package io.eventdriven.eventsversioning.testing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ThenCompatibilityStep<S, T> {
    private final Snapshot snapshot;
    private final S instance;
    private final Class<T> targetType;
    private final ObjectMapper mapper;

    ThenCompatibilityStep(Snapshot snapshot, S instance, Class<T> targetType, ObjectMapper mapper) {
        this.snapshot = snapshot;
        this.instance = instance;
        this.targetType = targetType;
        this.mapper = mapper;
    }

    public void thenForwardCompatible() {
        verifySharedFields(t -> {});
    }

    public void thenForwardCompatible(Consumer<T> extra) {
        verifySharedFields(extra);
    }

    public void thenBackwardCompatible() {
        verifySharedFields(t -> {});
    }

    public void thenBackwardCompatible(Consumer<T> extra) {
        verifySharedFields(extra);
    }

    private void verifySharedFields(Consumer<T> extra) {
        var sourceBytes = resolveSourceBytes();
        T deserialized;
        try {
            deserialized = mapper.readValue(sourceBytes, targetType);
        } catch (IOException e) {
            throw new RuntimeException("Deserialization as " + targetType.getSimpleName() + " failed", e);
        }
        if (deserialized == null) {
            fail("Deserialization as " + targetType.getSimpleName() + " returned empty");
        }
        assertSharedFieldsMatch(sourceBytes, deserialized);
        extra.accept(deserialized);
    }

    private void assertSharedFieldsMatch(byte[] sourceBytes, T target) {
        try {
            var sourceMap = toMap(sourceBytes);
            var targetMap = toMap(mapper.writeValueAsBytes(target));

            var sharedKeys = new HashSet<>(sourceMap.keySet());
            sharedKeys.retainAll(targetMap.keySet());

            for (var key : sharedKeys) {
                assertEquals(sourceMap.get(key), targetMap.get(key),
                    "Field '" + key + "' value differs between versions");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> toMap(byte[] bytes) throws IOException {
        return mapper.readValue(bytes, new TypeReference<>() {});
    }

    private byte[] resolveSourceBytes() {
        if (instance != null) {
            try {
                return mapper.writeValueAsBytes(instance);
            } catch (IOException e) {
                throw new RuntimeException("Serialization of " + instance.getClass().getSimpleName() + " failed", e);
            }
        }
        var path = switch (snapshot) {
            case Snapshot.ByClass<?> s -> SnapshotPathResolver.resolve(s.sourceType().getSimpleName());
            case Snapshot.ByMessageType s -> SnapshotPathResolver.resolve(s.messageType());
            case Snapshot.ByPath s -> s.path();
        };
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read snapshot file: " + path, e);
        }
    }
}
