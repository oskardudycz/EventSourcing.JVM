package io.eventdriven.distributedprocesses.core.commands;

import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

public record CommandEnvelope<Command>(Command data, CommandMetadata metadata)
    implements ResolvableTypeProvider {
  @Override
  public ResolvableType getResolvableType() {
    return ResolvableType.forClassWithGenerics(getClass(), ResolvableType.forInstance(data));
  }
}
