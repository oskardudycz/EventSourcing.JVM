package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

public class UnreadableMessageException extends RuntimeException {
  public UnreadableMessageException(String messageType, Throwable cause) {
    super("Unable to read message of type " + messageType, cause);
  }
}
