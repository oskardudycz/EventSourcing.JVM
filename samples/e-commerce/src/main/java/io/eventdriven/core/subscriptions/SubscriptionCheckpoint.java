package io.eventdriven.core.subscriptions;

import io.eventdriven.core.eventstore.Checkpoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processor_checkpoints")
public class SubscriptionCheckpoint {
    @Id
    @Column(name = "processor_id")
    private String subscriptionName;

    @Column(name = "last_processed_position")
    private long lastProcessedPosition;

    @Column(name = "last_processed_transaction_id", columnDefinition = "bigint not null default 0")
    private long lastProcessedTransactionId;

    protected SubscriptionCheckpoint() {}

    SubscriptionCheckpoint(String subscriptionName) {
        this.subscriptionName = subscriptionName;
    }

    Checkpoint checkpoint() {
        return new Checkpoint(lastProcessedTransactionId, lastProcessedPosition);
    }

    boolean isAtOrPast(Checkpoint next) {
        if (lastProcessedTransactionId != next.transactionId())
            return lastProcessedTransactionId > next.transactionId();

        return lastProcessedPosition >= next.position();
    }

    void moveTo(Checkpoint next) {
        lastProcessedTransactionId = next.transactionId();
        lastProcessedPosition = next.position();
    }
}
