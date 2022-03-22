package io.eventdriven.ecommerce.core.events;

public interface EventBus {
  <TEvent> void Publish(Class<TEvent> type, EventEnvelope<TEvent> event);
}
