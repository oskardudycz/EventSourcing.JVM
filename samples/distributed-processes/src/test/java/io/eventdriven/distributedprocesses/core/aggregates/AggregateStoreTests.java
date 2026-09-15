package io.eventdriven.distributedprocesses.core.aggregates;

import io.eventdriven.distributedprocesses.core.esdb.InMemoryEventStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

public class AggregateStoreTests {
  private final InMemoryEventStore eventStore = new InMemoryEventStore();
  private final AggregateStore<Counter, CounterEvent, UUID> store =
    new AggregateStore<>(eventStore, AggregateStoreTests::mapToStreamId, Counter::new);
  private final ArrayList<String> log = new ArrayList<>();

  private static final UUID counterId = UUID.randomUUID();

  @Test
  public void addThenGetRoundTripsState() {
    store.add(Counter.open(counterId, "clicks"));

    var counter = store.get(counterId).orElseThrow();

    assertThat(counter.id()).isEqualTo(counterId);
    assertThat(counter.name()).isEqualTo("clicks");
    assertThat(counter.total()).isZero();
  }

  @Test
  public void getOnUnknownIdIsEmpty() {
    assertThat(store.get(UUID.randomUUID())).isEmpty();
  }

  @Test
  public void versionAfterReplayEqualsEventCountMinusOne() {
    store.add(Counter.open(counterId, "clicks"));
    store.getAndUpdate(current -> current.increment(1), counterId);
    store.getAndUpdate(current -> current.increment(2), counterId);

    assertThat(store.get(counterId).orElseThrow().version).isEqualTo(2);
  }

  @Test
  public void getAndUpdateAppendsAndAdvancesTheRevision() {
    var afterAdd = store.add(Counter.open(counterId, "clicks"));

    var afterUpdate = store.getAndUpdate(current -> current.increment(3), counterId);

    assertThat(afterAdd.toLong()).isZero();
    assertThat(afterUpdate.toLong()).isEqualTo(1);
    assertThat(store.get(counterId).orElseThrow().total()).isEqualTo(3);
  }

  @Test
  public void addOnExistingStreamThrows() {
    store.add(Counter.open(counterId, "clicks"));

    assertThatThrownBy(() -> store.add(Counter.open(counterId, "clicks")))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(mapToStreamId(counterId));
  }

  @Test
  public void getAndUpdateWithStaleExplicitRevisionThrows() {
    store.add(Counter.open(counterId, "clicks"));
    store.getAndUpdate(current -> current.increment(1), counterId);

    assertThatThrownBy(() -> store.getAndUpdate(current -> current.increment(1), counterId, 0))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(mapToStreamId(counterId));

    assertThat(store.get(counterId).orElseThrow().total()).isEqualTo(1);
  }

  @Test
  public void subscriberOnTheStoreReceivesTheEventsThatAddAppended() {
    eventStore.subscribe(CounterEvent.Opened.class, event -> log.add("opened:" + event.name()));

    store.add(Counter.open(counterId, "clicks"));

    assertThat(log).containsExactly("opened:clicks");
  }

  private static String mapToStreamId(UUID id) {
    return "Counter-%s".formatted(id);
  }

  sealed interface CounterEvent {
    record Opened(UUID counterId, String name) implements CounterEvent {
    }

    record Incremented(UUID counterId, int by) implements CounterEvent {
    }
  }

  static class Counter extends AbstractAggregate<CounterEvent, UUID> {
    private String name;
    private int total;

    static Counter open(UUID counterId, String name) {
      var counter = new Counter();
      counter.enqueue(new CounterEvent.Opened(counterId, name));

      return counter;
    }

    void increment(int by) {
      enqueue(new CounterEvent.Incremented(id, by));
    }

    String name() {
      return name;
    }

    int total() {
      return total;
    }

    @Override
    public void when(CounterEvent event) {
      switch (event) {
        case CounterEvent.Opened opened -> {
          id = opened.counterId();
          name = opened.name();
        }
        case CounterEvent.Incremented incremented -> total += incremented.by();
      }
    }
  }
}
