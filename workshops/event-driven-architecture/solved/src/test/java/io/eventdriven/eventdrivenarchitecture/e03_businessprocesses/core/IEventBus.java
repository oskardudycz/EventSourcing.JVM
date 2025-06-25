package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import java.util.function.Consumer;

public interface IEventBus {
    void publish(Object[] events);
    <Event> IEventBus subscribe(Class<Event> eventClass, Consumer<Event> handler);
    void use(Consumer<Object> middleware);
}