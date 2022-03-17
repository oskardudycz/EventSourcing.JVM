package io.eventdriven.ecommerce.core.events;

import io.eventdriven.ecommerce.core.scopes.ServiceScope;
import org.springframework.core.ResolvableType;

public record EventBus(ServiceScope serviceScope) implements IEventBus {

  @Override
  public void Publish(Object... events) {
    for (var event : events) {
      serviceScope.run(scope -> {
        PublishEvent(scope, event);

        if(event instanceof EventEnvelope){
          // if event is wrapped with envelope, publish also data
          // for handlers that don't need metadata
          PublishEvent(scope, ((EventEnvelope<?>) event).data());
        }
      });
    }
  }

  private void PublishEvent(ServiceScope scope, Object event){
    var eventHandlers = scope.getBeansOfType(
      ResolvableType.forClassWithGenerics(IEventHandler.class, event.getClass()).resolve()
    );

    for (var eventHandler : eventHandlers.values()) {
      PublishEvent(event.getClass(), event, eventHandler);
    }
  }

  private <TEvent> void PublishEvent(final Class<TEvent> type, final Object event, Object eventHandler) {
    var typedEventHandler = (IEventHandler<TEvent>) eventHandler;
    var typedEvent = type.cast(event);

    typedEventHandler.handle(typedEvent);
  }
}
