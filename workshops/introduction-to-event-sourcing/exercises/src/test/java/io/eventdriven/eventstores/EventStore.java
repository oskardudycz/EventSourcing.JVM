package io.eventdriven.eventstores;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public interface EventStore {
  void init();

  default void appendEvents(
    StreamName streamName,
    Object... events
  ) {
    appendEvents(streamName, null, events);
  }

  void appendEvents(
    StreamName streamName,
    Long expectedVersion,
    Object... events
  );

  default List<Object> getEvents(
    StreamName streamName
  ) {
    return getEvents(streamName, null, null);
  }

  List<Object> getEvents(
    StreamName streamName,
    Long atStreamVersion,
    LocalDateTime atTimestamp
  );

  default <Stream, Event> Optional<Stream> aggregateStream(
    Supplier<Stream> getDefault,
    BiFunction<Stream, Event, Stream> evolve,
    StreamName streamName
  ) {
    var events = getEvents(streamName);

    if (events.isEmpty()) {
      return Optional.empty();
    }

    var aggregate = getDefault.get();

    for (var event : events) {
      aggregate = evolve.apply(aggregate, (Event) event);
    }

    return Optional.of(aggregate);
  }
}
