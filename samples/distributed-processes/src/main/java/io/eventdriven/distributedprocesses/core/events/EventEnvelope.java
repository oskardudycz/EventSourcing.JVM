package io.eventdriven.distributedprocesses.core.events;

import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

public record EventEnvelope<Event>(
  Event data,
  EventMetadata metadata
) implements ResolvableTypeProvider {
  @Override
  public ResolvableType getResolvableType() {
    return ResolvableType.forClassWithGenerics(
      getClass(), ResolvableType.forInstance(data)
    );
  }
}
