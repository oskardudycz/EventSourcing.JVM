package io.eventdriven.ecommerce.core.events;

public interface IEventBus {
  <TEvent> void Publish(Class<TEvent> type, EventEnvelope<TEvent> event);
}
