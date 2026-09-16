package io.eventdriven.distributedprocesses.core.aggregates;

import io.eventdriven.distributedprocesses.core.esdb.EventStore;
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
  public void openThenGetRoundTripsState() {
    store.getAndUpdate(counterId, current -> current.open(counterId, "clicks"));

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
    store.getAndUpdate(counterId, current -> current.open(counterId, "clicks"));
    store.getAndUpdate(counterId, current -> current.increment(1));
    store.getAndUpdate(counterId, current -> current.increment(2));

    assertThat(store.get(counterId).orElseThrow().version).isEqualTo(2);
  }

  @Test
  public void getAndUpdateAppendsAndAdvancesTheRevision() {
    var afterOpen = store.getAndUpdate(counterId, current -> current.open(counterId, "clicks"));

    var afterUpdate = store.getAndUpdate(counterId, current -> current.increment(3));

    assertThat(afterOpen.toLong()).isZero();
    assertThat(afterUpdate.toLong()).isEqualTo(1);
    assertThat(store.get(counterId).orElseThrow().total()).isEqualTo(3);
  }

  @Test
  public void aCommandThatEnqueuesNothingAppendsNothing() {
    store.getAndUpdate(counterId, current -> current.open(counterId, "clicks"));

    store.getAndUpdate(counterId, current -> current.open(counterId, "clicks"));

    assertThat(eventsIn(counterId)).hasSize(1);
  }

  @Test
  public void aCommandThatEnqueuesNothingDoesNotThrow() {
    store.getAndUpdate(counterId, current -> current.open(counterId, "clicks"));

    assertThatNoException().isThrownBy(
      () -> store.getAndUpdate(counterId, current -> current.open(counterId, "clicks"))
    );
  }

  @Test
  public void aCommandThatEnqueuesNothingReturnsTheRevisionTheStreamAlreadyHas() {
    store.getAndUpdate(counterId, current -> current.open(counterId, "clicks"));
    var afterIncrement = store.getAndUpdate(counterId, current -> current.increment(1));

    var afterRepeat = store.getAndUpdate(counterId, current -> current.open(counterId, "clicks"));

    assertThat(afterRepeat.toLong()).isEqualTo(afterIncrement.toLong());
  }

  @Test
  public void getAndUpdateWithStaleExplicitRevisionThrows() {
    store.getAndUpdate(counterId, current -> current.open(counterId, "clicks"));
    store.getAndUpdate(counterId, current -> current.increment(1));

    assertThatThrownBy(() -> store.getAndUpdate(counterId, 0, current -> current.increment(1)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(mapToStreamId(counterId));

    assertThat(store.get(counterId).orElseThrow().total()).isEqualTo(1);
  }

  @Test
  public void subscriberOnTheStoreReceivesTheEventsThatWereAppended() {
    eventStore.subscribe(CounterEvent.Opened.class, event -> log.add("opened:" + event.name()));

    store.getAndUpdate(counterId, current -> current.open(counterId, "clicks"));

    assertThat(log).containsExactly("opened:clicks");
  }

  private Object[] eventsIn(UUID id) {
    return switch (eventStore.read(mapToStreamId(id))) {
      case EventStore.ReadResult.Success success -> success.events();
      default -> new Object[0];
    };
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

    void open(UUID counterId, String name) {
      if (this.name != null)
        return;

      enqueue(new CounterEvent.Opened(counterId, name));
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
    public void evolve(CounterEvent event) {
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
