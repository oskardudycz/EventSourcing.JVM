package io.eventdriven.decider.core.processing;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public record Decider<State, Command, Event>(
  BiFunction<Command, State, Event[]> decide,
  BiFunction<State, Event, State> evolve,
  Supplier<State> getInitialState
) {
}
