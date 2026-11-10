package io.eventdriven.eventdrivenarchitecture.e05_consistency.core;

public class TimeoutException extends RuntimeException {
  public TimeoutException(String message) {
    super(message);
  }
}
