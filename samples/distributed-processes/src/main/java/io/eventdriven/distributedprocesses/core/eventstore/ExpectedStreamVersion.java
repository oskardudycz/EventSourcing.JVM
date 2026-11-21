package io.eventdriven.distributedprocesses.core.eventstore;

public sealed interface ExpectedStreamVersion {
    record NoStream() implements ExpectedStreamVersion {}

    record Any() implements ExpectedStreamVersion {}

    record StreamExists() implements ExpectedStreamVersion {}

    record At(long version) implements ExpectedStreamVersion {}

    static ExpectedStreamVersion noStream() {
        return new NoStream();
    }

    static ExpectedStreamVersion any() {
        return new Any();
    }

    static ExpectedStreamVersion streamExists() {
        return new StreamExists();
    }

    static ExpectedStreamVersion at(long version) {
        return new At(version);
    }
}
