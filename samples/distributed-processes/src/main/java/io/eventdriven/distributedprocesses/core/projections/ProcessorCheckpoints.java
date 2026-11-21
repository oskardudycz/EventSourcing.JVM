package io.eventdriven.distributedprocesses.core.projections;

import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class ProcessorCheckpoints {
    private final ProcessorCheckpointRepository repository;
    private final TransactionTemplate transaction;

    public ProcessorCheckpoints(
            ProcessorCheckpointRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public Optional<Checkpoint> lastProcessed(String processorId) {
        return repository.findById(processorId).map(ProcessorCheckpoint::checkpoint);
    }

    public OptionalLong lastProcessedPosition(String processorId) {
        return lastProcessed(processorId)
                .map(checkpoint -> OptionalLong.of(checkpoint.position()))
                .orElseGet(OptionalLong::empty);
    }

    public void handle(String processorId, long position, Runnable work) {
        handle(processorId, 0, position, work);
    }

    public void handle(String processorId, long transactionId, long position, Runnable work) {
        var next = new Checkpoint(transactionId, position);

        transaction.executeWithoutResult(status -> {
            work.run();

            var stored = repository.findById(processorId);

            if (stored.isPresent() && stored.get().isAtOrPast(next)) {
                status.setRollbackOnly();
                return;
            }

            var checkpoint = stored.orElseGet(() -> new ProcessorCheckpoint(processorId));
            checkpoint.moveTo(next);
            repository.save(checkpoint);
        });
    }
}
