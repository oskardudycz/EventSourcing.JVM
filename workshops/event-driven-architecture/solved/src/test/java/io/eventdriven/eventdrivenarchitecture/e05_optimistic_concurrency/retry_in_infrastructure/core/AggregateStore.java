package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.retry_in_infrastructure.core;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Aggregate;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Retry;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core.Versioned;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public class AggregateStore<A extends Aggregate<UUID>> {
    private final Database.Collection<A> collection;

    public AggregateStore(Database.Collection<A> collection) {
        this.collection = collection;
    }

    public List<Object> handle(UUID id, Function<Optional<A>, Optional<A>> handle) {
        return Retry.onConflict(() -> {
            var loaded = collection.get(id);

            var aggregate = handle.apply(loaded.map(Versioned::document));
            if (aggregate.isEmpty()) return List.of();

            var events = aggregate.get().dequeueUncommittedEvents();
            if (events.isEmpty()) return events;

            collection.store(id, aggregate.get(), loaded.map(Versioned::version).orElse(0));

            return events;
        });
    }
}
