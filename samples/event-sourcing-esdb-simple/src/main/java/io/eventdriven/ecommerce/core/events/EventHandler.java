package io.eventdriven.ecommerce.core.events;

import java.util.function.Consumer;

public class EventHandler<TEvent> implements IEventHandler<TEvent>
{
  private Class<TEvent> eventType;
  private Consumer<EventEnvelope<TEvent>> consumer;

  private EventHandler(Class<TEvent> eventType, Consumer<EventEnvelope<TEvent>> consumer) {
    this.eventType = eventType;
    this.consumer = consumer;
  }

  public static <TEvent> EventHandler<TEvent> of(Class<TEvent> eventType, Consumer<EventEnvelope<TEvent>> consumer){
    return new EventHandler<>(eventType, consumer);
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
