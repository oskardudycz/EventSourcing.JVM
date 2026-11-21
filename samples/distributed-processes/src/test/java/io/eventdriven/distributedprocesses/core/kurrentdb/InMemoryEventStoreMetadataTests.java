package io.eventdriven.distributedprocesses.core.kurrentdb;

public class InMemoryEventStoreMetadataTests extends EventStoreMetadataTests {
    private final InMemoryEventStore eventStore = new InMemoryEventStore(context);

    @Override
    protected EventStore eventStore() {
        return eventStore;
    }
}
