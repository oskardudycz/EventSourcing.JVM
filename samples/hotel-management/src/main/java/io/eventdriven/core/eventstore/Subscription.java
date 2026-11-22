package io.eventdriven.core.eventstore;

import java.time.Duration;

public interface Subscription {
    void whenProcessed(Checkpoint checkpoint, Duration timeout);

    default void whenProcessed(Checkpoint checkpoint) {
        whenProcessed(checkpoint, Duration.ofSeconds(30));
    }
}
