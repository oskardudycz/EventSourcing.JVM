package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

public class ConcurrencyException extends RuntimeException {
    public ConcurrencyException(String message) {
        super(message);
    }
}
