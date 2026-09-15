package io.eventdriven.distributedprocesses.core.messaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class InMemoryEventBus implements IntegrationEventBus {
  private final Map<Class<?>, List<Consumer<Object>>> handlers = new HashMap<>();
  private final List<Consumer<Object>> middlewares = new ArrayList<>();

  @Override
  public <Event> void publish(Event... events) {
    for (var event : events) {
      for (var middleware : middlewares) {
        middleware.accept(event);
      }

      for (var handler : handlers.getOrDefault(event.getClass(), List.of())) {
        handler.accept(event);
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Event> EventBus subscribe(Class<Event> type, Consumer<Event> handler) {
    handlers
      .computeIfAbsent(type, ignored -> new ArrayList<>())
      .add((Consumer<Object>) handler);

    return this;
  }

  @Override
  public EventBus use(Consumer<Object> middleware) {
    middlewares.add(middleware);

    return this;
  }
}
