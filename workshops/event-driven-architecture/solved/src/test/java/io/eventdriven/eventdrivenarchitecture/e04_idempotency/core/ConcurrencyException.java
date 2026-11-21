package io.eventdriven.eventdrivenarchitecture.e04_idempotency.core;

public class ConcurrencyException extends RuntimeException {
    public ConcurrencyException(String message) {
        super(message);
    }
}
