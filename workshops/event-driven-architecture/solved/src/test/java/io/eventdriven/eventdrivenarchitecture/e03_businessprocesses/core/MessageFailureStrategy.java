package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import java.util.Optional;

/**
 * Decides what happens to a message whose handling failed.
 * <p>
 * The broker adapters ask it on every failure and apply the decision with the broker's own
 * retry and dead-letter mechanisms. Decide by the error (will a retry help?) and by the
 * message (how important is it to keep processing in order?). The message is empty when
 * it couldn't be read.
 */
public interface MessageFailureStrategy {
  enum Decision {RETRY, DEAD_LETTER}

  Decision onFailure(String messageType, Optional<Object> message, Throwable error);

  static MessageFailureStrategy retryUnlessUnreadable() {
    return (messageType, message, error) -> isCausedBy(error, UnreadableMessageException.class)
      ? Decision.DEAD_LETTER
      : Decision.RETRY;
  }

  private static boolean isCausedBy(Throwable error, Class<? extends Throwable> type) {
    for (var current = error; current != null; current = current.getCause()) {
      if (type.isInstance(current))
        return true;
    }
    return false;
  }
}
