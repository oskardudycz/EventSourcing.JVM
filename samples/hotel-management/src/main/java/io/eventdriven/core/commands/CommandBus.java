package io.eventdriven.core.commands;

import io.eventdriven.core.eventstore.EventStore;
import java.util.function.Consumer;

public interface CommandBus {
    <Command> EventStore.AppendResult schedule(Command command);

    void subscribe(Consumer<CommandEnvelope<Object>>... handlers);
}
