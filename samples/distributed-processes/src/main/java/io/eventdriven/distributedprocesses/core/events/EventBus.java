package io.eventdriven.distributedprocesses.core.events;

import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import java.util.function.Consumer;

public interface EventBus {
  <Event> EventStore.AppendResult publish(Event event);

  void subscribe(Consumer<EventEnvelope<Object>>... handlers);
}
