package io.eventdriven.core.projections;

public sealed interface CounterEvent {
    CounterId counterId();

    record Started(CounterId counterId) implements CounterEvent {}

    record Incremented(CounterId counterId) implements CounterEvent {}

    record Paused(CounterId counterId) implements CounterEvent {}

    record Finished(CounterId counterId) implements CounterEvent {}
}
