package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.AbstractAggregate;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.ConcurrencyException;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class AggregateStoreTest {
    @Test
    public void functionReturningNewAggregate_StoresItWithVersion1AndReturnsItsEvents() {
        var events = store.handle(id, _ -> Optional.of(Counter.create(id)));

        assertThat(events).containsExactly(new Counter.Incremented(1));
        assertThat(counters.get(id).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    public void functionReceivesLoadedAggregate_AndItsChangesAreStored() {
        store.handle(id, _ -> Optional.of(Counter.create(id)));

        var events = store.handle(id, counter -> counter.map(Counter::increment));

        assertThat(events).containsExactly(new Counter.Incremented(2));
        assertThat(counters.get(id).orElseThrow().document().value).isEqualTo(2);
        assertThat(counters.get(id).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    public void nothingStored_FunctionReceivesEmptyOptional() {
        var received = new AtomicInteger();

        store.handle(id, counter -> {
            if (counter.isEmpty()) received.incrementAndGet();
            return Optional.empty();
        });

        assertThat(received).hasValue(1);
    }

    @Test
    public void functionReturningNothing_StoresNothingAndReturnsNoEvents() {
        var events = store.handle(id, _ -> Optional.empty());

        assertThat(events).isEmpty();
        assertThat(counters.get(id)).isEmpty();
    }

    @Test
    public void aggregateWithoutEvents_IsNotStored() {
        store.handle(id, _ -> Optional.of(Counter.create(id)));

        var events = store.handle(id, counter -> counter);

        assertThat(events).isEmpty();
        assertThat(counters.get(id).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    public void conflict_RunsFunctionAgainOnFreshlyLoadedAggregateAndReturnsOnlyLastAttemptEvents() {
        store.handle(id, _ -> Optional.of(Counter.create(id)));
        var attempts = new AtomicInteger();

        var events = store.handle(id, counter -> {
            if (attempts.incrementAndGet() == 1) concurrentlyIncrement();
            return counter.map(Counter::increment);
        });

        assertThat(attempts).hasValue(2);
        assertThat(events).containsExactly(new Counter.Incremented(3));
        assertThat(counters.get(id).orElseThrow().document().value).isEqualTo(3);
    }

    @Test
    public void conflictOnEveryAttempt_ThrowsAfterTenAttempts() {
        store.handle(id, _ -> Optional.of(Counter.create(id)));
        var attempts = new AtomicInteger();

        assertThrows(
                ConcurrencyException.class,
                () -> store.handle(id, counter -> {
                    attempts.incrementAndGet();
                    concurrentlyIncrement();
                    return counter.map(Counter::increment);
                }));

        assertThat(attempts).hasValue(10);
    }

    private void concurrentlyIncrement() {
        var loaded = counters.get(id).orElseThrow();
        loaded.document().increment();
        counters.store(id, loaded.document(), loaded.version());
    }

    private final UUID id = UUID.randomUUID();
    private final Database.Collection<Counter> counters = new Database().collection(Counter.class);
    private final AggregateStore<Counter> store = new AggregateStore<>(counters);

    public static class Counter extends AbstractAggregate<Counter.Incremented, UUID> {
        public record Incremented(int value) {}

        int value;

        private Counter() {}

        static Counter create(UUID id) {
            var counter = new Counter();
            counter.id = id;
            return counter.increment();
        }

        Counter increment() {
            enqueue(new Incremented(value + 1));
            return this;
        }

        @Override
        public void apply(Incremented event) {
            value = event.value();
        }
    }
}
