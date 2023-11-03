package io.eventdriven.distributedprocesses.core.commands;

import io.eventdriven.distributedprocesses.core.esdb.EventStore;
import java.util.function.Consumer;

public interface CommandBus {
  <Command> EventStore.AppendResult schedule(Command command);

  void subscribe(Consumer<CommandEnvelope<Object>>... handlers);
}
