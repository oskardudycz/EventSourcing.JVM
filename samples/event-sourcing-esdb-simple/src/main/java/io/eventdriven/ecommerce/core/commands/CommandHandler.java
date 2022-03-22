package io.eventdriven.ecommerce.core.commands;

import io.eventdriven.ecommerce.core.http.ETag;

import java.util.concurrent.ExecutionException;

@FunctionalInterface
public interface CommandHandler<Command> {
  ETag handle(Command command) throws ExecutionException, InterruptedException;
}