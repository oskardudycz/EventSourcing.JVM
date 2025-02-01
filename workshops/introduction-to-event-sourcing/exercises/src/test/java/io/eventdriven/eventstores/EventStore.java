package io.eventdriven.eventstores;

import java.util.List;
import java.util.Optional;
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

  default <Stream, Event> Optional<Stream> aggregateStream(
    Supplier<Stream> getDefault,
    BiFunction<Stream, Event, Stream> evolve,
    StreamName streamName
  ) {
    var events = readStream(streamName);

    if (events.isEmpty()) {
      return Optional.empty();
    }

    var aggregate = getDefault.get();

    for (var event : events) {
      aggregate = evolve.apply(aggregate, (Event) event);
    }

    return Optional.of(aggregate);
  }

  record AppendResult(long nextExpectedStreamPosition) {
  }
}
