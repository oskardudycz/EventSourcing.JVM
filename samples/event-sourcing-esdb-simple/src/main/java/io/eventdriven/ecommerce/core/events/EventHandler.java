package io.eventdriven.ecommerce.core.events;

public interface EventHandler<TEvent>
{
  Class<TEvent> getEventType();

  void handle(EventEnvelope<TEvent> event);
}
