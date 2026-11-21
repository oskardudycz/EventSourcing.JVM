package io.eventdriven.distributedprocesses.core.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.eventstore.Checkpoint;
import io.eventdriven.testing.ProjectionsDatabase;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class SubscriptionCheckpointsTests {
    private final SubscriptionCheckpoints checkpoints = ProjectionsDatabase.H2.bean(SubscriptionCheckpoints.class);
    private final String processorId = "test-%s".formatted(UUID.randomUUID());

    @Test
    public void positionOfAnEventInAnOlderTransactionIsPastTheCheckpointEvenWhenItIsHigher() {
        checkpoints.handle(processorId, 101, 5, () -> {});

        checkpoints.handle(processorId, 100, 7, () -> {});

        assertThat(checkpoints.lastProcessed(processorId))
                .hasValueSatisfying(checkpoint -> assertThat(checkpoint).isEqualTo(new Checkpoint(101, 5)));
    }

    @Test
    public void positionOfAnEventInANewerTransactionIsNotPastTheCheckpointEvenWhenItIsLower() {
        checkpoints.handle(processorId, 101, 5, () -> {});

        checkpoints.handle(processorId, 102, 3, () -> {});

        assertThat(checkpoints.lastProcessed(processorId))
                .hasValueSatisfying(checkpoint -> assertThat(checkpoint).isEqualTo(new Checkpoint(102, 3)));
    }

    @Test
    public void positionInTheSameTransactionIsComparedByThePosition() {
        checkpoints.handle(processorId, 101, 5, () -> {});

        checkpoints.handle(processorId, 101, 5, () -> {});
        checkpoints.handle(processorId, 101, 6, () -> {});

        assertThat(checkpoints.lastProcessed(processorId))
                .hasValueSatisfying(checkpoint -> assertThat(checkpoint).isEqualTo(new Checkpoint(101, 6)));
    }

    @Test
    public void aCheckpointStoredWithoutATransactionIdIsComparedByThePosition() {
        checkpoints.handle(processorId, 5, () -> {});

        checkpoints.handle(processorId, 4, () -> {});

        assertThat(checkpoints.lastProcessedPosition(processorId)).hasValue(5);
    }
}
