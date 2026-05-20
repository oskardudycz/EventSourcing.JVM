package io.eventdriven.eventsversioning.testing;

import com.fasterxml.jackson.databind.ObjectMapper;

public class GivenStep<S> {
    final Snapshot snapshot;
    final S instance;
    final ObjectMapper mapper;

    GivenStep(Snapshot snapshot, S instance, ObjectMapper mapper) {
        this.snapshot = snapshot;
        this.instance = instance;
        this.mapper = mapper;
    }

    public ThenContractStep<S> whenSerialized() {
        requireInstance();
        return new ThenContractStep<>(instance, null, mapper);
    }

    public ThenContractStep<S> whenSerialized(Snapshot destination) {
        requireInstance();
        return new ThenContractStep<>(instance, destination, mapper);
    }

    private void requireInstance() {
        if (instance == null) {
            throw new IllegalStateException("whenSerialized() requires an instance — use Contract.given(instance), not Contract.given(Snapshot)");
        }
    }

    public <T> ThenCompatibilityStep<S, T> whenDeserializedAs(Class<T> targetType) {
        return new ThenCompatibilityStep<>(snapshot, instance, targetType, mapper);
    }
}
