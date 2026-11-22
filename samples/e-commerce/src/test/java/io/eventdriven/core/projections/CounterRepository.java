package io.eventdriven.core.projections;

import java.util.List;

public interface CounterRepository extends ProjectionRepository<Counter, CounterId> {
    List<Counter> findByCountGreaterThan(int count);
}
