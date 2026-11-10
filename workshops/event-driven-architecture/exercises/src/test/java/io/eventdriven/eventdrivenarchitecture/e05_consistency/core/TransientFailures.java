package io.eventdriven.eventdrivenarchitecture.e05_consistency.core;

import java.util.concurrent.ThreadLocalRandom;

// Fails at a random call, then again after 2 to 4 calls, so failures land in a different place on every run, but never twice in a row
public class TransientFailures {
  private final String message;
  private int callsUntilFailure = nextGap();

  public TransientFailures(String message) {
    this.message = message;
  }

  public void failIfUnavailable() {
    if (--callsUntilFailure > 0)
      return;

    callsUntilFailure = nextGap();
    throw new TimeoutException(message);
  }

  private static int nextGap() {
    return ThreadLocalRandom.current().nextInt(2, 5);
  }
}
