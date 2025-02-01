package io.eventdriven.eventstores;

import java.util.List;
import java.util.function.BiFunction;
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

  List<Object> readStream(StreamName streamName);

  default <State, Event> State aggregateStream(
    Supplier<State> getDefault,
    BiFunction<State, Event, State> evolve,
    StreamName streamName
  ) {
    var events = readStream(streamName);

    var state = getDefault.get();

    for (var event : events) {
      state = evolve.apply(state, (Event) event);
    }

    return state;
  }

  record AppendResult(long nextExpectedStreamPosition) {
  }
}
