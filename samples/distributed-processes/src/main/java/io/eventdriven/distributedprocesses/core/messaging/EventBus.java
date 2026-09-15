package io.eventdriven.distributedprocesses.core.messaging;

import java.util.function.Consumer;

public interface EventBus {
  <Event> void publish(Event... events);

  <Event> EventBus subscribe(Class<Event> type, Consumer<Event> handler);

  EventBus use(Consumer<Object> middleware);
}
