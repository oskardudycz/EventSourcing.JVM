package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {
  private final ConcurrentHashMap<Class<?>, List<Consumer<Object>>> eventHandlers = new ConcurrentHashMap<>();
  private final List<Consumer<Object>> middlewares = new ArrayList<>();

  public void publish(Object[] events) {
    for (Object event : events) {
      for (Consumer<Object> middleware : middlewares) {
        middleware.accept(event);
      }

      List<Consumer<Object>> handlers = eventHandlers.get(event.getClass());
      if (handlers == null) continue;

      for (Consumer<Object> handler : handlers) {
        handler.accept(event);
      }
    }
  }

  public <T> void subscribe(Class<T> eventType, Consumer<T> eventHandler) {
    Consumer<Object> handler = obj -> eventHandler.accept(eventType.cast(obj));

    eventHandlers.compute(eventType, (key, existingHandlers) -> {
      if (existingHandlers == null) {
        List<Consumer<Object>> handlers = new ArrayList<>();
        handlers.add(handler);
        return handlers;
      } else {
        existingHandlers.add(handler);
        return existingHandlers;
      }
    });
  }

  public void use(Consumer<Object> middleware) {
    middlewares.add(middleware);
  }
}
