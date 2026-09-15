package io.eventdriven.distributedprocesses.core.esdb;

import com.eventstore.dbclient.*;
import io.eventdriven.distributedprocesses.core.serialization.EventSerializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class ESDBEventStore implements EventStore {
  @Override
  public ReadResult read(String streamId) {
    try {
      var result = eventStore.readStream(streamId, ReadStreamOptions.get()).get();

      var events = result.getEvents().stream()
        .map(resolvedEvent -> EventSerializer.deserialize(resolvedEvent))
        .flatMap(Optional::stream)
        .toArray();

      return new ReadResult.Success(events);
    } catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof StreamNotFoundException) {
        return new ReadResult.StreamDoesNotExist();
      }
      return new ReadResult.UnexpectedFailure(e);
    }
  }

  @Override
  public AppendResult append(String streamId, Object... events) {
    var eventsToAppend = Arrays.stream(events)
      .map(EventSerializer::serialize)
      .toList();

    try {
      var result = eventStore.appendToStream(
        streamId,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.noStream()),
        eventsToAppend.iterator()
      ).get();

      return new AppendResult.Success(result.getNextExpectedRevision(), result.getLogPosition());
    } catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof WrongExpectedVersionException wrongExpectedVersionException) {
        return new AppendResult.StreamAlreadyExists(wrongExpectedVersionException.getActualVersion());
      }

      return new AppendResult.UnexpectedFailure(e);
    }
  }

  @Override
  public AppendResult append(String streamId, ExpectedRevision expectedRevision, Object... events) {
    try {
      var eventsToAppend = Arrays.stream(events)
        .map(EventSerializer::serialize)
        .toList();

      var result = eventStore.appendToStream(
        streamId,
        AppendToStreamOptions.get().expectedRevision(expectedRevision),
        eventsToAppend.iterator()
      ).get();

      return new AppendResult.Success(result.getNextExpectedRevision(), result.getLogPosition());
    } catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof WrongExpectedVersionException wrongExpectedVersionException) {
        return new AppendResult.Conflict(expectedRevision, wrongExpectedVersionException.getActualVersion());
      }
      return new AppendResult.UnexpectedFailure(e);
    }
  }

  public DeleteResult deleteStream(String streamId) {
    try {
      eventStore.deleteStream(
        streamId,
        DeleteStreamOptions.get().expectedRevision(ExpectedRevision.streamExists())
      ).get();

      return new DeleteResult.Success();
    } catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof WrongExpectedVersionException) {
        return new DeleteResult.StreamDoesNotExist();
      }
      return new DeleteResult.UnexpectedFailure(e);
    }
  }

  public DeleteResult deleteStream(String streamId, ExpectedRevision expectedRevision) {
    try {
      eventStore.deleteStream(
        streamId,
        DeleteStreamOptions.get().expectedRevision(expectedRevision)
      ).get();

      return new DeleteResult.Success();
    } catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof WrongExpectedVersionException) {
        return new DeleteResult.StreamDoesNotExist();
      }
      return new DeleteResult.UnexpectedFailure(e);
    }
  }



  public AppendResult setStreamMaxAge(String streamId, Duration duration) {
    try {
      var metadata = new StreamMetadata();
      metadata.setMaxAge(duration.toSeconds());

      var result = eventStore.setStreamMetadata(
        streamId,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.noStream()),
        metadata
      ).get();

      return new AppendResult.Success(result.getNextExpectedRevision(), result.getLogPosition());
    } catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof WrongExpectedVersionException wrongExpectedVersionException) {
        return new AppendResult.StreamAlreadyExists(wrongExpectedVersionException.getActualVersion());
      }

      return new AppendResult.UnexpectedFailure(e);
    }
  }

  private final EventStoreDBClient eventStore;

  public ESDBEventStore(EventStoreDBClient eventStoreDBClient) {
    this.eventStore = eventStoreDBClient;
  }
}
