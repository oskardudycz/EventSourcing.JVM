package io.eventdriven.ecommerce.core.commands;

import io.eventdriven.ecommerce.core.http.ETag;

import java.util.concurrent.ExecutionException;

@FunctionalInterface
public interface CommandHandler<TCommand> {
  ETag handle(TCommand command) throws ExecutionException, InterruptedException;
}
