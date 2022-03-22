package io.eventdriven.ecommerce.core.events;

public interface EventHandler<Event>
{
  Class<Event> getEventType();

  void handle(EventEnvelope<Event> event);
}
