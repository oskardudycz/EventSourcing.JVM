package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class Projection<Document> {
  private final Database.Collection<Document> collection;

  protected Projection(Database.Collection<Document> collection) {
    this.collection = collection;
  }

  public abstract List<Class<?>> canHandle();

  protected abstract Optional<UUID> getDocumentId(Object event);

  protected abstract @Nullable Document evolve(@Nullable Document current, Object event);

  public Optional<Document> handle(Object event) {
    return getDocumentId(event).flatMap(id -> {
      var document = evolve(collection.get(id).orElse(null), event);

      if (document == null) {
        collection.delete(id);
        return Optional.empty();
      }

      collection.store(id, document);
      return Optional.of(document);
    });
  }
}
