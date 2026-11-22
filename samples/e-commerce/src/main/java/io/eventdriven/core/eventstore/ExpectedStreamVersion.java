package io.eventdriven.core.eventstore;

public sealed interface ExpectedStreamVersion {
    record NoStream() implements ExpectedStreamVersion {}

    record Any() implements ExpectedStreamVersion {}

    record StreamExists() implements ExpectedStreamVersion {}

    record At(long version) implements ExpectedStreamVersion {
        public At {
            if (version < 0) throw new IllegalArgumentException("Stream version %s is negative".formatted(version));
        }
    }

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
