package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.ConcurrencyException;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class CommandHandlerTest {
    @Test
    public void nothingStored_FunctionReceivesInitialState_AndResultIsStoredWithVersion1() {
        var received = new AtomicInteger(-1);

        var events = handler.handle(id, state -> {
            received.set(state.value());
            return List.of(new Incremented(1));
        });

        assertThat(received).hasValue(0);
        assertThat(events).containsExactly(new Incremented(1));
        assertThat(counters.get(id).orElseThrow().document()).isEqualTo(new Counter(1));
        assertThat(counters.get(id).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    public void storedState_FunctionReceivesIt_AndEvolvedStateIsStored() {
        handler.handle(id, _ -> List.of(new Incremented(1)));

        var events = handler.handle(id, state -> List.of(new Incremented(state.value() + 1)));

        assertThat(events).containsExactly(new Incremented(2));
        assertThat(counters.get(id).orElseThrow().document()).isEqualTo(new Counter(2));
        assertThat(counters.get(id).orElseThrow().version()).isEqualTo(2);
    }

    @Test
    public void functionReturningNoEvents_StoresNothing() {
        var events = handler.handle(id, _ -> List.of());

        assertThat(events).isEmpty();
        assertThat(counters.get(id)).isEmpty();
    }

    @Test
    public void functionReturningNoEvents_KeepsStoredVersion() {
        handler.handle(id, _ -> List.of(new Incremented(1)));

        var events = handler.handle(id, _ -> List.of());

        assertThat(events).isEmpty();
        assertThat(counters.get(id).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    public void conflict_RunsFunctionAgainOnFreshlyLoadedStateAndReturnsOnlyLastAttemptEvents() {
        handler.handle(id, _ -> List.of(new Incremented(1)));
        var attempts = new AtomicInteger();

        var events = handler.handle(id, state -> {
            if (attempts.incrementAndGet() == 1) concurrentlyIncrement();
            return List.of(new Incremented(state.value() + 1));
        });

        assertThat(attempts).hasValue(2);
        assertThat(events).containsExactly(new Incremented(3));
        assertThat(counters.get(id).orElseThrow().document()).isEqualTo(new Counter(3));
    }

    @Test
    public void conflictOnEveryAttempt_ThrowsAfterTenAttempts() {
        handler.handle(id, _ -> List.of(new Incremented(1)));
        var attempts = new AtomicInteger();

        assertThrows(
                ConcurrencyException.class,
                () -> handler.handle(id, state -> {
                    attempts.incrementAndGet();
                    concurrentlyIncrement();
                    return List.of(new Incremented(state.value() + 1));
                }));

        assertThat(attempts).hasValue(10);
    }

    private void concurrentlyIncrement() {
        var loaded = counters.get(id).orElseThrow();
        counters.store(id, new Counter(loaded.document().value() + 1), loaded.version());
    }

    private final UUID id = UUID.randomUUID();
    private final Database.Collection<Counter> counters = new Database().collection(Counter.class);
    private final CommandHandler<Counter, Incremented> handler =
            new CommandHandler<>(counters, new Counter(0), Counter::evolve);

    public record Incremented(int value) {}

    public record Counter(int value) {
        static Counter evolve(Counter state, Incremented event) {
            return new Counter(event.value());
        }
    }
}
