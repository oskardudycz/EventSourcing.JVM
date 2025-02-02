package io.eventdriven.eventstores;

import com.eventstore.dbclient.ExpectedRevision;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public interface EventStore {
  void init();

  default AppendResult appendToStream(
    StreamName streamName,
    List<Object> events
  ) {
    return appendToStream(streamName, null, events);
  }

  AppendResult appendToStream(
    StreamName streamName,
    Long expectedStreamPosition,
    List<Object> events
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
    Long expectedStreamPosition,
    Function<State, List<Event>> handle
  ) {
    var aggregationResult = aggregateStream(getInitial, evolve, streamName);

    var events = handle.apply(aggregationResult.state());

    if (events.isEmpty()) {
      return new AppendResult(aggregationResult.currentStreamPosition());
    }

    return appendToStream(streamName, expectedStreamPosition, new ArrayList<>(events));
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

    return appendToStream(streamName, aggregationResult.currentStreamPosition, new ArrayList<>(events));
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
      return currentStreamPosition > 0;
    }
  }

  class InvalidExpectedStreamPositionException extends RuntimeException {
    private final String streamName;
    private final Long expectedStreamPosition;

    public InvalidExpectedStreamPositionException(String streamName, Long expectedStreamPosition) {
      super(String.format("Expected %s but got other instead", expectedStreamPosition));

      this.streamName = streamName;
      this.expectedStreamPosition = expectedStreamPosition;
    }

    public String getStreamName() {
      return this.streamName;
    }

    public Long getExpectedStreamPosition() {
      return this.expectedStreamPosition;
    }
  }
}
