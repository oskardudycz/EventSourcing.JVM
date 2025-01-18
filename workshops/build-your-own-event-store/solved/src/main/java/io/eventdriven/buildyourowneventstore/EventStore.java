package io.eventdriven.buildyourowneventstore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public interface EventStore {
  void init();

  default <Stream> void appendEvents(
    Class<Stream> streamClass,
    String streamId,
    Object... events
  ) {
    appendEvents(streamClass, streamId, null, events);
  }

  <Stream> void appendEvents(
    Class<Stream> streamClass,
    String streamId,
    Long expectedVersion,
    Object... events
  );

  default List<Object> getEvents(
    String streamId
  ) {
    return getEvents(streamId, null, null);
  }

  List<Object> getEvents(
    String streamId,
    Long atStreamVersion,
    LocalDateTime atTimestamp
  );

  default <Stream, Event> Optional<Stream> aggregateStream(
    Supplier<Stream> getDefault,
    BiFunction<Stream, Event, Stream> evolve,
    String streamId
  ) {
    return aggregateStream(getDefault, evolve, streamId, null, null);
  }

  default <Stream, Event> Optional<Stream> aggregateStream(
    Supplier<Stream> getDefault,
    BiFunction<Stream, Event, Stream> evolve,
    String streamId,
    Long atStreamVersion,
    LocalDateTime atTimestamp
  ) {
    var events = getEvents(streamId, atStreamVersion, atTimestamp);

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
