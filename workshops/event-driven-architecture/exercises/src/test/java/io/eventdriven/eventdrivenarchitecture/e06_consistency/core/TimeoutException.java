package io.eventdriven.eventdrivenarchitecture.e06_consistency.core;

public class TimeoutException extends RuntimeException {
  public TimeoutException(String message) {
    super(message);
  }
}
