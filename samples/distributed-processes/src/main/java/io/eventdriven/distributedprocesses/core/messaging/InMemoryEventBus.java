package io.eventdriven.distributedprocesses.core.messaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

// Internal and integration channels are separate interfaces on purpose, so that
// a module cannot subscribe to another module's internals. In this sample one
// implementation serves both; in production they would be different transports.
public class InMemoryEventBus implements InternalEventBus, IntegrationEventBus {
  private static final String defaultStreamId = "integration";

  private final Map<Class<?>, List<Consumer<Object>>> handlers = new HashMap<>();
  private final Map<Class<?>, List<EventHandler<Object>>> envelopeHandlers = new HashMap<>();
  private final List<Consumer<Object>> middlewares = new ArrayList<>();
  private long position = -1;

  @Override
  public <Event> void publish(Event... events) {
    publishAt(defaultStreamId, position + 1, events);
  }

  public void publishAt(String streamId, long firstPosition, Object... events) {
    for (var i = 0; i < events.length; i++) {
      var event = events[i];
      var metadata = new EventMetadata(streamId, firstPosition + i);

      position = Math.max(position, metadata.streamPosition());

      for (var middleware : middlewares) {
        middleware.accept(event);
      }

      for (var handler : handlers.getOrDefault(event.getClass(), List.of())) {
        handler.accept(event);
      }

      for (var handler : envelopeHandlers.getOrDefault(event.getClass(), List.of())) {
        handler.handle(new EventEnvelope<>(event, metadata));
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Event> InMemoryEventBus subscribe(Class<Event> type, Consumer<Event> handler) {
    handlers
      .computeIfAbsent(type, ignored -> new ArrayList<>())
      .add((Consumer<Object>) handler);

    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <Event> InMemoryEventBus subscribeWithMetadata(Class<Event> type, EventHandler<Event> handler) {
    envelopeHandlers
      .computeIfAbsent(type, ignored -> new ArrayList<>())
      .add((EventHandler<Object>) handler);

    return this;
  }

  @Override
  public InMemoryEventBus use(Consumer<Object> middleware) {
    middlewares.add(middleware);

    return this;
  }
}
