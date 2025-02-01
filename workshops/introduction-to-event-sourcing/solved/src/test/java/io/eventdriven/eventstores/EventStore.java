package io.eventdriven.eventstores;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public interface EventStore {
  void init();

  default AppendResult appendToStream(
    StreamName streamName,
    Object... events
  ) {
    return appendToStream(streamName, null, events);
  }

  AppendResult appendToStream(
    StreamName streamName,
    Long expectedStreamPosition,
    Object... events
  );

  ReadStreamResult readStream(StreamName streamName);

  default <State, Event> StreamAggregationResult<State> aggregateStream(
    Supplier<State> getInitial,
    BiFunction<State, Event, State> evolve,
    StreamName streamName
  ) {
    var readResult = readStream(streamName);

    var state = getInitial.get();

    for (var event : readResult.events()) {
      state = evolve.apply(state, (Event) event);
    }

    return new StreamAggregationResult<>(
      readResult.currentStreamPosition,
      state
    );
  }

  default <State, Event> AppendResult getAndUpdate(
    Supplier<State> getInitial,
    BiFunction<State, Event, State> evolve,
    StreamName streamName,
    Function<State, List<Event>> handle
  ) {
    var aggregationResult = aggregateStream(getInitial, evolve, streamName);

    var events = handle.apply(aggregationResult.state());

    if (events.isEmpty()) {
      return new AppendResult(aggregationResult.currentStreamPosition());
    }

    return appendToStream(streamName, events);
  }

  record AppendResult(long nextExpectedStreamPosition) {
  }

  record ReadStreamResult(
    long currentStreamPosition,
    List<Object> events
  ) {
    public boolean streamExists() {
      return currentStreamPosition == 0;
    }
  }

  record StreamAggregationResult<State>(
    long currentStreamPosition,
    State state
  ) {
    public boolean streamExists() {
      return currentStreamPosition == 0;
    }
  }
}
