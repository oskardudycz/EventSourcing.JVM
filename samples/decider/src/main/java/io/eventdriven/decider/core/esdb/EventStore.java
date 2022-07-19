package io.eventdriven.decider.core.esdb;

import com.eventstore.dbclient.*;
import io.eventdriven.decider.core.processing.ETag;
import io.eventdriven.decider.core.serialization.EventSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class EventStore {
  public <Event> List<Event> read(String streamId) {
    try {
      return eventStore.readStream(streamId).get().getEvents().stream()
        .map(EventSerializer::<Event>deserialize)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
    } catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof StreamNotFoundException) {
        return new ArrayList<>();
      }
      throw new RuntimeException(e);
    }
  }

  public AppendResult append(String streamId, ETag eTag, Object... events) {
    try {
      var eventsToAppend = Arrays.stream(events)
        .map(EventSerializer::serialize)
        .toList();

      var expectedRevision =
        !eTag.isEmpty() ? ExpectedRevision.expectedRevision(eTag.toLong()) : ExpectedRevision.NO_STREAM;

      var result = eventStore.appendToStream(
        streamId,
        AppendToStreamOptions.get().expectedRevision(expectedRevision),
        eventsToAppend.iterator()
      ).get();

      return new AppendResult.Success(ETag.weak(result.getNextExpectedRevision()));
    } catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof WrongExpectedVersionException wrongExpectedVersionException) {
        return new AppendResult.Conflict(eTag, ETag.weak(wrongExpectedVersionException.getActualVersion()));
      }
      throw new RuntimeException(e);
    }
  }

  private final EventStoreDBClient eventStore;

  public EventStore(EventStoreDBClient eventStoreDBClient) {
    this.eventStore = eventStoreDBClient;
  }

  sealed public interface AppendResult {
    record Success(ETag nextExpectedRevision) implements AppendResult {
    }

    record Conflict(ETag expected, ETag actual) implements AppendResult {
    }
  }
}
