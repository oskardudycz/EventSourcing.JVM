package io.eventdriven.distributedprocesses.core.messaging;

import java.util.function.Consumer;

public interface CommandBus {
  <Command> void send(Command... commands);

  <Command> CommandBus handle(Class<Command> type, Consumer<Command> handler);

  CommandBus use(Consumer<Object> middleware);
}
