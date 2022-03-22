package io.eventdriven.ecommerce.core.events;

import java.util.function.Consumer;

public class EventHandlerWrapper<Event> implements EventHandler<Event>
{
  private Class<Event> eventType;
  private Consumer<EventEnvelope<Event>> consumer;

  private EventHandlerWrapper(Class<Event> eventType, Consumer<EventEnvelope<Event>> consumer) {
    this.eventType = eventType;
    this.consumer = consumer;
  }

  public static <Event> EventHandlerWrapper<Event> of(Class<Event> eventType, Consumer<EventEnvelope<Event>> consumer){
    return new EventHandlerWrapper<>(eventType, consumer);
  }

  @Override
  public Class<Event> getEventType() {
    return eventType;
  }

  @Override
  public void handle(EventEnvelope<Event> event) {
    consumer.accept(event);
  }
}
