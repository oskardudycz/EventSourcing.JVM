package io.eventdriven.distributedprocesses.core.eventstore;

import java.time.Duration;

public interface Subscription {
    void awaitProcessed(Checkpoint checkpoint, Duration timeout);
}
