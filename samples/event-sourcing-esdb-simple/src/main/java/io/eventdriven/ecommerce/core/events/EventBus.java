package io.eventdriven.ecommerce.core.events;

public interface EventBus {
  <Event> void Publish(Class<Event> type, EventEnvelope<Event> event);
}
