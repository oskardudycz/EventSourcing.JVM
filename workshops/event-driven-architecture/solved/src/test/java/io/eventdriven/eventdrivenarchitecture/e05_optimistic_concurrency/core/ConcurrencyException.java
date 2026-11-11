package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core;

public class ConcurrencyException extends RuntimeException {
  public ConcurrencyException(String message) {
    super(message);
  }
}
