package io.eventdriven.ecommerce.core.events;

public interface IEventHandler<TEvent>
{
  void handle(TEvent event);
}
