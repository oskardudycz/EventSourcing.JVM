package io.eventdriven.ecommerce.core.events;

import java.util.function.Consumer;

public class EventHandlerWrapper<TEvent> implements EventHandler<TEvent>
{
  private Class<TEvent> eventType;
  private Consumer<EventEnvelope<TEvent>> consumer;

  private EventHandlerWrapper(Class<TEvent> eventType, Consumer<EventEnvelope<TEvent>> consumer) {
    this.eventType = eventType;
    this.consumer = consumer;
  }

  public static <TEvent> EventHandlerWrapper<TEvent> of(Class<TEvent> eventType, Consumer<EventEnvelope<TEvent>> consumer){
    return new EventHandlerWrapper<>(eventType, consumer);
  }

  @Override
  public Class<TEvent> getEventType() {
    return eventType;
  }

  @Override
  public void handle(EventEnvelope<TEvent> event) {
    consumer.accept(event);
  }
}
