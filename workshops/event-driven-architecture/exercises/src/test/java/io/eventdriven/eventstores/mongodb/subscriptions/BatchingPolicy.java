package io.eventdriven.eventstores.mongodb.subscriptions;

import java.time.Duration;

public record BatchingPolicy(int batchSize, Duration maxWait) {
  public static BatchingPolicy ofSize(int size) {
    return new BatchingPolicy(size, Duration.ofMillis(100));
  }

  public static BatchingPolicy ofTime(Duration maxWait) {
    return new BatchingPolicy(1000, maxWait);
  }

  public static BatchingPolicy of(int size, Duration maxWait) {
    return new BatchingPolicy(size, maxWait);
  }

  public static final BatchingPolicy DEFAULT = of(10, Duration.ofMillis(100));
}
