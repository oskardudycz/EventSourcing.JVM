package io.eventdriven.ecommerce.core.events;

public interface EventBus {
  <Event> void publish(EventEnvelope<Event> event);
}
