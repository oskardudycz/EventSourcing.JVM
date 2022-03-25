package io.eventdriven.ecommerce.core.commands;

import io.eventdriven.ecommerce.core.http.ETag;

@FunctionalInterface
public interface CommandHandler<Command> {
  ETag handle(Command command);
}
