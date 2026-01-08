package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import java.util.List;
import java.util.function.Consumer;

public interface ICommandBus {
    <Command> void send(List<Command> commands);
    <Command> ICommandBus handle(Class<Command> commandClass, Consumer<Command> handler);
    void use(Consumer<Object> middleware);
}
