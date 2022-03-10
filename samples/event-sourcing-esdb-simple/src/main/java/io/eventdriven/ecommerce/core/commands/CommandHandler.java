package io.eventdriven.ecommerce.core.commands;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@FunctionalInterface
public interface CommandHandler<TCommand> {
  CompletableFuture<Void> handle(TCommand command) throws ExecutionException, InterruptedException;
}
