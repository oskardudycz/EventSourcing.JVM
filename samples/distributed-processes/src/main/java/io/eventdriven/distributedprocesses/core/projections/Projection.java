package io.eventdriven.distributedprocesses.core.projections;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public abstract class Projection<Document extends Versioned> {
  private final Class<Document> documentType;
  private final Database database;

  protected Projection(Class<Document> documentType, Database database) {
    this.documentType = documentType;
    this.database = database;
  }

  public abstract List<Class<?>> canHandle();

  protected abstract EntityId getDocumentId(Object event);

  protected abstract @Nullable Document evolve(@Nullable Document current, Object event);

  public void handle(EventEnvelope<?> envelope) {
    var event = envelope.data();
    var streamPosition = envelope.metadata().streamPosition();
    var id = getDocumentId(event);
    var current = database.get(documentType, id).orElse(null);

    if (current != null && current.getVersion() >= streamPosition)
      return;

    var document = evolve(current, event);

    if (document == null) {
      database.delete(documentType, id);
      return;
    }

    database.store(documentType, id, streamPosition, document);
  }

  public Optional<Document> get(EntityId id) {
    return database.get(documentType, id);
  }

  protected List<Document> find(Predicate<Document> filter) {
    return database.find(documentType, filter);
  }
}
