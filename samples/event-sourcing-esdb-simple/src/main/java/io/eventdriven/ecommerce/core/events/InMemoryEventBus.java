package io.eventdriven.ecommerce.core.events;

import io.eventdriven.ecommerce.core.scopes.ServiceScope;
import org.springframework.core.ResolvableType;

public record InMemoryEventBus(ServiceScope serviceScope) implements EventBus {

  @Override
  public <Event> void publish(Class<Event> type, EventEnvelope<Event> event) {
    serviceScope.run(scope -> {
      publishInScope(type, scope, event);
    });
  }

  private <Event> void publishInScope(final Class<Event> type, ServiceScope scope, final EventEnvelope<Event> event) {
    var eventHandlers =
      scope.getBeansOfType(
          ResolvableType.forClassWithGenerics(EventHandler.class, type).resolve()
        )
        .values()
        .stream()
        .map(eh -> (EventHandler<Event>) eh)
        .filter(eh -> eh.getEventType().isInstance(event.data()))
        .toList();

    for (var eventHandler : eventHandlers) {
      eventHandler.handle(event);
    }
  }
}