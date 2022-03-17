package io.eventdriven.ecommerce.core.events;

public interface IEventBus {
  void Publish(Object... events);
}
