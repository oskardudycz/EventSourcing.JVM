package io.eventdriven.eventdrivenarchitecture.e05_consistency.core;

public final class Retry {
  private static final int MAX_ATTEMPTS = 10;

  public static void untilSucceeds(Runnable operation) {
    for (var attempt = 1; ; attempt++) {
      try {
        operation.run();
        return;
      } catch (TimeoutException exception) {
        if (attempt >= MAX_ATTEMPTS)
          throw exception;
      }
    }
  }
}
