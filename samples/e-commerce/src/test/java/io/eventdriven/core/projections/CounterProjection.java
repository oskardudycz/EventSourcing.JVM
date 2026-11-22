package io.eventdriven.core.projections;

import io.eventdriven.core.projections.CounterEvent.*;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class CounterProjection extends Projection<Counter, CounterId> {
    private final CounterRepository repository;

    public CounterProjection(CounterRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(Started.class, Incremented.class, Paused.class, Finished.class);
    }

    @Override
    protected CounterId getDocumentId(Object event) {
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

    public List<CounterId> countedAtLeastOnce() {
        return repository.findByCountGreaterThan(0).stream()
                .map(Counter::getCounterId)
                .toList();
    }
}
