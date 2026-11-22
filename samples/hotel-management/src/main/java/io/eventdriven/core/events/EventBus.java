package io.eventdriven.core.events;

import io.eventdriven.core.eventstore.EventStore;
import java.util.function.Consumer;

public interface EventBus {
    <Event> EventStore.AppendResult publish(Event event);

    void subscribe(Consumer<EventEnvelope<Object>>... handlers);
}
