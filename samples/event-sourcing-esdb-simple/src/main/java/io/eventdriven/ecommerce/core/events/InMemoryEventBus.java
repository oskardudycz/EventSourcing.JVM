package io.eventdriven.ecommerce.core.events;

import io.eventdriven.ecommerce.core.scopes.ServiceScope;
import org.springframework.core.ResolvableType;

public record InMemoryEventBus(ServiceScope serviceScope) implements EventBus {

  @Override
  public <TEvent> void Publish(Class<TEvent> type, EventEnvelope<TEvent> event) {
    serviceScope.run(scope -> {
      PublishEvent(type, scope, event);
    });
  }

  private <TEvent> void PublishEvent(final Class<TEvent> type, ServiceScope scope, final EventEnvelope<TEvent> event) {
    var eventHandlers =
      scope.getBeansOfType(
          ResolvableType.forClassWithGenerics(EventHandler.class, type).resolve()
        )
        .values()
        .stream()
        .map(eh -> (EventHandler<TEvent>) eh)
        .filter(eh -> eh.getEventType().isInstance(event.data()))
        .toList();

    for (var eventHandler : eventHandlers) {
      eventHandler.handle(event);
    }
  }
}
