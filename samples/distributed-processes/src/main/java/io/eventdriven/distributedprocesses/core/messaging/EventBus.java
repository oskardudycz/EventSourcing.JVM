package io.eventdriven.distributedprocesses.core.messaging;

import java.util.function.Consumer;

public interface EventBus {
  <Event> void publish(Event... events);

  <Event> EventBus subscribe(Class<Event> type, Consumer<Event> handler);

  <Event> EventBus subscribeWithMetadata(Class<Event> type, EventHandler<Event> handler);

  EventBus use(Consumer<Object> middleware);
}
