package io.eventdriven.core.eventstore.inmemory;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.EventStoreMetadataTests;

public class InMemoryEventStoreMetadataTests extends EventStoreMetadataTests {
    private final InMemoryEventStore eventStore = new InMemoryEventStore(context, serializer());

    @Override
    protected EventStore eventStore() {
        return eventStore;
    }
}
