package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core;

import java.util.function.Supplier;

public final class Retry {
  private static final int MAX_ATTEMPTS = 10;

  public static <T> T onConflict(Supplier<T> operation) {
    for (var attempt = 1; ; attempt++) {
      try {
        return operation.get();
      } catch (ConcurrencyException exception) {
        if (attempt >= MAX_ATTEMPTS)
          throw exception;
      }
    }
  }
}
