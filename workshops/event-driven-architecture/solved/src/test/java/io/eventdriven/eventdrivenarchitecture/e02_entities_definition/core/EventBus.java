package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EventBus {
  private final Map<String, List<Consumer<Object>>> handlers = new ConcurrentHashMap<>();
  private final List<Consumer<Object>> middlewares = new ArrayList<>();

  public void publish(Object[] events) {
    for (Object event : events) {

      for (var middleware: middlewares)
        middleware.accept(event);

      var eventHandlers = handlers.get(event.getClass().getTypeName());

      for (var handle : eventHandlers) {
        handle.accept(event);
      }
    }
  }

  public <Event> void subscribe(Class<Event> eventClass, Consumer<Object> handler) {
    handlers.compute(eventClass.getTypeName(), (eventType, consumers) -> {
      if (consumers == null)
        consumers = new ArrayList<>();

      consumers.add(
        event -> handler.accept(eventClass.cast(event))
      );

      return consumers;
    });
  }

  public void use(Consumer<Object> middleware) {
    middlewares.add(middleware);
  }
}
