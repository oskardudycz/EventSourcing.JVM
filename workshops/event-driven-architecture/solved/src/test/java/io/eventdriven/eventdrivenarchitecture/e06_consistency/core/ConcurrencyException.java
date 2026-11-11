package io.eventdriven.eventdrivenarchitecture.e06_consistency.core;

public class ConcurrencyException extends RuntimeException {
  public ConcurrencyException(String message) {
    super(message);
  }
}
