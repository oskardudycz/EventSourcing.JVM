package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.core;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectionTest {
  record Counter(UUID id, int count) {
  }

  record Started(UUID counterId) {
  }

  record Incremented(@Nullable UUID counterId) {
  }

  record Finished(UUID counterId) {
  }

  static class CounterProjection extends Projection<Counter> {
    CounterProjection(Database.Collection<Counter> collection) {
      super(collection);
    }

    @Override
    public List<Class<?>> canHandle() {
      return List.of(Started.class, Incremented.class, Finished.class);
    }

    @Override
    protected Optional<UUID> getDocumentId(Object event) {
      return switch (event) {
        case Started e -> Optional.of(e.counterId());
        case Incremented e -> Optional.ofNullable(e.counterId());
        case Finished e -> Optional.of(e.counterId());
        default -> Optional.empty();
      };
    }

    @Override
    protected @Nullable Counter evolve(@Nullable Counter current, Object event) {
      return switch (event) {
        case Started e -> new Counter(e.counterId(), 0);
        case Incremented e -> new Counter(e.counterId(), current.count() + 1);
        case Finished _ -> null;
        default -> current;
      };
    }
  }

  private final Database.Collection<Counter> collection = new Database().collection(Counter.class);
  private final CounterProjection projection = new CounterProjection(collection);

  @Test
  public void firstEvent_StoresAndReturnsTheDocument() {
    var id = UUID.randomUUID();

    var result = projection.handle(new Started(id));

    assertThat(result).contains(new Counter(id, 0));
    assertThat(collection.get(id)).contains(new Counter(id, 0));
  }

  @Test
  public void nextEvent_EvolvesTheStoredDocument() {
    var id = UUID.randomUUID();
    projection.handle(new Started(id));

    var result = projection.handle(new Incremented(id));

    assertThat(result).contains(new Counter(id, 1));
    assertThat(collection.get(id)).contains(new Counter(id, 1));
  }

  @Test
  public void eventWithoutDocumentId_IsSkipped() {
    var result = projection.handle(new Incremented(null));

    assertThat(result).isEmpty();
  }

  @Test
  public void noDocumentFromEvolve_DeletesTheDocument() {
    var id = UUID.randomUUID();
    projection.handle(new Started(id));

    var result = projection.handle(new Finished(id));

    assertThat(result).isEmpty();
    assertThat(collection.get(id)).isEmpty();
  }

  @Test
  public void canHandle_ListsTheProjectedEvents() {
    assertThat(projection.canHandle()).containsExactly(Started.class, Incremented.class, Finished.class);
  }
}
