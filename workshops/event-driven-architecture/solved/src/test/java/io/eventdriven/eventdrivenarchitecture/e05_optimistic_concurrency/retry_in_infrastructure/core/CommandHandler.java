package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.core;

import static io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.FunctionalTools.FoldLeft.reduce;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Versioned;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

public class CommandHandler<State, Event> {
    private final Database.Collection<State> collection;
    private final State initialState;
    private final BiFunction<State, Event, State> evolve;

    public CommandHandler(
            Database.Collection<State> collection, State initialState, BiFunction<State, Event, State> evolve) {
        this.collection = collection;
        this.initialState = initialState;
        this.evolve = evolve;
    }

    public List<Event> handle(UUID id, Function<State, List<Event>> handle) {
        return Retry.onConflict(() -> {
            var loaded = collection.get(id);
            var state = loaded.map(Versioned::document).orElse(initialState);

            var events = handle.apply(state);
            if (events.isEmpty()) return events;

            collection.store(
                    id,
                    reduce(events, state, evolve),
                    loaded.map(Versioned::version).orElse(0));

            return events;
        });
    }
}
