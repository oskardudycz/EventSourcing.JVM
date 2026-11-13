package io.eventdriven.distributedprocesses.core.projections;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.EventMetadata;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectionTests {
  private final CounterId counterId = new CounterId(UUID.randomUUID().toString());
  private final CounterId otherCounterId = new CounterId(UUID.randomUUID().toString());
  private final CounterProjection projection = new CounterProjection(new Database());

  @Test
  public void firstEventStoresTheDocumentAtItsStreamPosition() {
    projection.handle(at(0, new Started(counterId)));

    assertThat(projection.get(counterId)).hasValueSatisfying(counter -> {
      assertThat(counter.getCount()).isEqualTo(0);
      assertThat(counter.getVersion()).isEqualTo(0);
    });
  }

  @Test
  public void nextEventEvolvesTheStoredDocument() {
    projection.handle(at(0, new Started(counterId)));

    projection.handle(at(1, new Incremented(counterId)));

    assertThat(projection.get(counterId)).hasValueSatisfying(counter -> {
      assertThat(counter.getCount()).isEqualTo(1);
      assertThat(counter.getVersion()).isEqualTo(1);
    });
  }

  @Test
  public void eventDeliveredAgainIsSkipped() {
    projection.handle(at(0, new Started(counterId)));
    var incremented = at(1, new Incremented(counterId));

    projection.handle(incremented);
    projection.handle(incremented);

    assertThat(projection.get(counterId)).hasValueSatisfying(counter -> assertThat(counter.getCount()).isEqualTo(1));
  }

  @Test
  public void eventOlderThanTheDocumentIsSkipped() {
    projection.handle(at(0, new Started(counterId)));
    projection.handle(at(2, new Incremented(counterId)));

    projection.handle(at(1, new Incremented(counterId)));

    assertThat(projection.get(counterId)).hasValueSatisfying(counter -> {
      assertThat(counter.getCount()).isEqualTo(1);
      assertThat(counter.getVersion()).isEqualTo(2);
    });
  }

  @Test
  public void eventThatChangesNoFieldStillMovesTheVersion() {
    projection.handle(at(0, new Started(counterId)));

    projection.handle(at(1, new Paused(counterId)));

    assertThat(projection.get(counterId)).hasValueSatisfying(counter -> {
      assertThat(counter.getCount()).isEqualTo(0);
      assertThat(counter.getVersion()).isEqualTo(1);
    });
  }

  @Test
  public void noDocumentFromEvolveDeletesTheDocument() {
    projection.handle(at(0, new Started(counterId)));

    projection.handle(at(1, new Finished(counterId)));

    assertThat(projection.get(counterId)).isEmpty();
  }

  @Test
  public void queryFindsOnlyTheMatchingDocuments() {
    projection.handle(at(0, new Started(counterId)));
    projection.handle(at(1, new Incremented(counterId)));
    projection.handle(at(0, new Started(otherCounterId)));

    assertThat(projection.countedAtLeastOnce()).containsExactly(counterId);
  }

  private <Event> EventEnvelope<Event> at(long streamPosition, Event event) {
    return new EventEnvelope<>(event, new EventMetadata("Counter", streamPosition, streamPosition));
  }

  record CounterId(String value) implements EntityId {
  }

  record Started(CounterId counterId) {
  }

  record Incremented(CounterId counterId) {
  }

  record Paused(CounterId counterId) {
  }

  record Finished(CounterId counterId) {
  }

  static class Counter implements Versioned {
    private CounterId counterId;
    private int count;
    private long version;

    Counter() {
    }

    Counter(CounterId counterId) {
      this.counterId = counterId;
    }

    CounterId getCounterId() {
      return counterId;
    }

    int getCount() {
      return count;
    }

    void increment() {
      count++;
    }

    @Override
    public long getVersion() {
      return version;
    }

    @Override
    public void setVersion(long version) {
      this.version = version;
    }
  }

  static class CounterProjection extends Projection<Counter> {
    CounterProjection(Database database) {
      super(Counter.class, database);
    }

    @Override
    public List<Class<?>> canHandle() {
      return List.of(Started.class, Incremented.class, Paused.class, Finished.class);
    }

    @Override
    protected EntityId getDocumentId(Object event) {
      return switch (event) {
        case Started e -> e.counterId();
        case Incremented e -> e.counterId();
        case Paused e -> e.counterId();
        case Finished e -> e.counterId();
        default -> throw new IllegalArgumentException("Unknown event " + event);
      };
    }

    @Override
    protected @Nullable Counter evolve(@Nullable Counter current, Object event) {
      return switch (event) {
        case Started e -> new Counter(e.counterId());
        case Incremented _ -> {
          current.increment();
          yield current;
        }
        case Finished _ -> null;
        default -> current;
      };
    }

    List<CounterId> countedAtLeastOnce() {
      return find(counter -> counter.getCount() > 0).stream().map(Counter::getCounterId).toList();
    }
  }
}
