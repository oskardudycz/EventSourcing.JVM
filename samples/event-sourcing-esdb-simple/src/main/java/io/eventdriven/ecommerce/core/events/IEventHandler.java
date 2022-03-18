package io.eventdriven.ecommerce.core.events;

public interface IEventHandler<TEvent>
{
  Class<TEvent> getEventType();

  void handle(EventEnvelope<TEvent> event);
}
