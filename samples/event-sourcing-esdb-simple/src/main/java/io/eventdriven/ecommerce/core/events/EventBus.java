package io.eventdriven.ecommerce.core.events;

public interface EventBus {
  <Event> void publish(Class<Event> type, EventEnvelope<Event> event);
}
