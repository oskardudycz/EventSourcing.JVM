package io.eventdriven.core.subscriptions;

import io.eventdriven.core.eventstore.Checkpoint;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class SubscriptionCheckpoints {
    private final SubscriptionCheckpointRepository repository;
    private final TransactionTemplate transaction;

    public SubscriptionCheckpoints(
            SubscriptionCheckpointRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Optional<Checkpoint> lastProcessed(String subscriptionName) {
        return repository.findById(subscriptionName).map(SubscriptionCheckpoint::checkpoint);
    }

    public OptionalLong lastProcessedPosition(String subscriptionName) {
        return lastProcessed(subscriptionName)
                .map(checkpoint -> OptionalLong.of(checkpoint.position()))
                .orElseGet(OptionalLong::empty);
    }

    public void handle(String subscriptionName, long position, Runnable work) {
        handle(subscriptionName, 0, position, work);
    }

    public void handle(String subscriptionName, long transactionId, long position, Runnable work) {
        var next = new Checkpoint(transactionId, position);

        transaction.executeWithoutResult(status -> {
            work.run();

            var stored = repository.findById(subscriptionName);

            if (stored.isPresent() && stored.get().isAtOrPast(next)) {
                status.setRollbackOnly();
                return;
            }

            var checkpoint = stored.orElseGet(() -> new SubscriptionCheckpoint(subscriptionName));
            checkpoint.moveTo(next);
            repository.save(checkpoint);
        });
    }
}
