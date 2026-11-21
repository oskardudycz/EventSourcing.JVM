package io.eventdriven.distributedprocesses.core.projections;

import java.util.List;

interface CounterRepository extends ProjectionRepository<Counter, CounterId> {
    List<Counter> findByCountGreaterThan(int count);
}
