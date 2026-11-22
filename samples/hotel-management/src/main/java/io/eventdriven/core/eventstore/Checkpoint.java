package io.eventdriven.core.eventstore;

import java.util.Comparator;

public record Checkpoint(long transactionId, long position) implements Comparable<Checkpoint> {
    private static final Comparator<Checkpoint> ORDER =
            Comparator.comparingLong(Checkpoint::transactionId).thenComparingLong(Checkpoint::position);

    @Override
    public int compareTo(Checkpoint other) {
        return ORDER.compare(this, other);
    }
}
