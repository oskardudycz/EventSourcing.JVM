package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.kurrentdb.core.eventstore;

import io.kurrent.dbclient.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import io.eventdriven.eventstores.EventStore;
import io.eventdriven.eventstores.StreamName;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class KurrentDBEventStore implements EventStore {
  private final KurrentDBClient eventStore;
  private final ObjectMapper mapper;

  public KurrentDBEventStore(
    KurrentDBClient eventStore,
    ObjectMapper mapper
  ) {
    this.eventStore = eventStore;
    this.mapper = mapper;
  }

  @Override
  public void init() {

  }

  @Override
  public ReadStreamResult readStream(StreamName streamName) {
    try {
      var readResult = eventStore.readStream(streamName.toString(), ReadStreamOptions.get()).get();

      var events = readResult.getEvents().stream()
        .map(this::deserialize)
        .toList();

      return new ReadStreamResult(readResult.getLastStreamPosition(), events);
    } catch (Throwable e) {
      Throwable innerException = e.getCause();

      if (innerException instanceof StreamNotFoundException) {
        return new ReadStreamResult(0, List.of());
      }
      throw new RuntimeException(e);
    }
  }

  @Override
  public AppendResult appendToStream(StreamName streamName, Long expectedStreamPosition, List<Object> events) {
    try {
      var expectedRevision = expectedStreamPosition != null ?
        StreamState.streamRevision(expectedStreamPosition)
        : StreamState.noStream();

      var result = eventStore.appendToStream(
        streamName.toString(),
        AppendToStreamOptions.get().streamState(expectedRevision),
        events.stream().map(this::serialize).iterator()
      ).get();

      return new AppendResult(result.getNextExpectedRevision().toRawLong());
    } catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private EventData serialize(Object event) {
    try {
      return EventDataBuilder.json(
        UUID.randomUUID(),
        event.getClass().getTypeName(),
        mapper.writeValueAsBytes(event)
      ).build();
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }

  private Object deserialize(ResolvedEvent resolvedEvent) {
    try {
      var eventClass = Class.forName(
        resolvedEvent.getOriginalEvent().getEventType());
      return mapper.readValue(resolvedEvent.getEvent().getEventData(), eventClass);
    } catch (JacksonException | ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
}
