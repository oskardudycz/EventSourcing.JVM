package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import java.util.List;
import java.util.function.Consumer;

public interface IEventBus {
    <Event> void publish(List<Event> events);
    <Event> IEventBus subscribe(Class<Event> eventClass, Consumer<Event> handler);
    void use(Consumer<Object> middleware);
}
