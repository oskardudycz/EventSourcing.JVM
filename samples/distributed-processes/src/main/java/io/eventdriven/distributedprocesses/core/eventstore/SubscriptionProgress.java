package io.eventdriven.distributedprocesses.core.eventstore;

import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class SubscriptionProgress implements Subscription {
    private final String name;
    private final Lock lock = new ReentrantLock();
    private final Condition advanced = lock.newCondition();
    private Checkpoint handled;

    public SubscriptionProgress(String name) {
        this.name = name;
    }

    public void handled(Checkpoint checkpoint) {
        lock.lock();
        try {
            if (handled == null || checkpoint.compareTo(handled) > 0) handled = checkpoint;
            advanced.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void awaitProcessed(Checkpoint checkpoint, Duration timeout) {
        var remaining = timeout.toNanos();

        lock.lock();
        try {
            while (handled == null || handled.compareTo(checkpoint) < 0) {
                if (remaining <= 0)
                    throw new IllegalStateException(
                            "Subscription '%s' didn't handle the event at %s in time".formatted(name, checkpoint));

                remaining = advanced.awaitNanos(remaining);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Waiting for subscription '%s' to handle the event at %s was interrupted"
                            .formatted(name, checkpoint),
                    e);
        } finally {
            lock.unlock();
        }
    }
}
