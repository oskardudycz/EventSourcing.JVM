package io.eventdriven.eventdrivenarchitecture.e06_consistency.core;

import java.util.function.Supplier;

public final class Retry {
  private static final int MAX_ATTEMPTS = 10;

  public static void untilSucceeds(Runnable operation) {
    untilSucceeds(() -> {
      operation.run();
      return null;
    });
  }

  public static <T> T untilSucceeds(Supplier<T> operation) {
    return retry(operation, TimeoutException.class);
  }

  public static void onConflict(Runnable operation) {
    onConflict(() -> {
      operation.run();
      return null;
    });
  }

  public static <T> T onConflict(Supplier<T> operation) {
    return retry(operation, ConcurrencyException.class);
  }

  private static <T> T retry(Supplier<T> operation, Class<? extends RuntimeException> retried) {
    for (var attempt = 1; ; attempt++) {
      try {
        return operation.get();
      } catch (RuntimeException exception) {
        if (!retried.isInstance(exception) || attempt >= MAX_ATTEMPTS)
          throw exception;
      }
    }
  }
}
