package io.eventdriven.core.projections;

import io.eventdriven.core.identifiers.EntityId;
import io.eventdriven.core.messaging.EventEnvelope;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public abstract class Projection<Document extends Versioned, Id extends EntityId> {
    private final ProjectionRepository<Document, Id> repository;

    protected Projection(ProjectionRepository<Document, Id> repository) {
        this.repository = repository;
    }

    public abstract List<Class<?>> canHandle();

    protected abstract Id getDocumentId(Object event);

    protected abstract @Nullable Document evolve(@Nullable Document current, Object event);

    public void handle(EventEnvelope<?> envelope) {
        repository.inTransaction(() -> apply(envelope));
    }

    private void apply(EventEnvelope<?> envelope) {
        var event = envelope.data();
        var streamPosition = envelope.metadata().streamPosition();
        var id = getDocumentId(event);
        var current = repository.findById(id).orElse(null);

        if (current != null && current.getVersion() >= streamPosition) return;

        var document = evolve(current, event);

        if (document == null) {
            if (current != null) repository.delete(current);
            return;
        }

        document.setVersion(streamPosition);
        repository.save(document);
    }

    public Optional<Document> get(Id id) {
        return repository.findById(id);
    }
}
