package io.eventdriven.eventsversioning.testing;

import java.nio.file.Path;

public sealed interface Snapshot permits Snapshot.ByClass, Snapshot.ByMessageType, Snapshot.ByPath {

    record ByClass<T>(Class<T> sourceType) implements Snapshot {}

    record ByMessageType(String messageType, Class<?> sourceType) implements Snapshot {}

    record ByPath(Path path, Class<?> sourceType) implements Snapshot {}

    static <T> ByClass<T> of(Class<T> sourceType) {
        return new ByClass<>(sourceType);
    }

    static ByPath at(Path path) {
        return new ByPath(path, null);
    }

    static <T> ByPath at(Path path, Class<T> sourceType) {
        return new ByPath(path, sourceType);
    }

    static ByMessageType forMessageType(String messageType) {
        return new ByMessageType(messageType, null);
    }

    static <T> ByMessageType forMessageType(String messageType, Class<T> sourceType) {
        return new ByMessageType(messageType, sourceType);
    }
}
