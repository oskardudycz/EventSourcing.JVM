package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

public interface ITestableMessageBus extends IMessageBus {
    void clearMiddleware();
}
