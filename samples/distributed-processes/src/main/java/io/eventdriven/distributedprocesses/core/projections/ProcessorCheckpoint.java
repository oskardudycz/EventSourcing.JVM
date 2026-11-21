package io.eventdriven.distributedprocesses.core.projections;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processor_checkpoints")
public class ProcessorCheckpoint {
    @Id
    @Column(name = "processor_id")
    private String processorId;

    @Column(name = "last_processed_position")
    private long lastProcessedPosition;

    @Column(name = "last_processed_transaction_id", columnDefinition = "bigint not null default 0")
    private long lastProcessedTransactionId;

    protected ProcessorCheckpoint() {}

    ProcessorCheckpoint(String processorId) {
        this.processorId = processorId;
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
