package io.eventdriven.distributedprocesses.core.messaging;

// Consumer<Event> and Consumer<EventEnvelope<Event>> erase to the same signature,
// so a handler that wants metadata subscribes through this type instead.
@FunctionalInterface
public interface EventHandler<Event> {
  void handle(EventEnvelope<Event> envelope);
}
