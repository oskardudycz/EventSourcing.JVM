package io.eventdriven.distributedprocesses.core.esdb;

import com.eventstore.dbclient.*;
import io.eventdriven.distributedprocesses.core.serialization.EventSerializer;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class EventStore {
  public ReadResult read(String streamId) {
    try {
      var result = eventStore.readStream(streamId, ReadStreamOptions.get()).get();

      return new ReadResult.Success(result.getEvents().toArray(new ResolvedEvent[0]));
    } catch (InterruptedException | ExecutionException e) {
      if (e.getCause() instanceof StreamNotFoundException) {
        return new ReadResult.StreamDoesNotExist();
      }
      return new ReadResult.UnexpectedFailure(e);
    }
  }

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

  public EventStore(EventStoreDBClient eventStoreDBClient) {
    this.eventStore = eventStoreDBClient;
  }

  public sealed interface ReadResult {
    record Success(
      ResolvedEvent[] events
    ) implements ReadResult {
    }

    record NoEventsFound() implements ReadResult {
    }

    record StreamDoesNotExist() implements ReadResult {
    }

    record UnexpectedFailure(Throwable t) implements ReadResult {
    }

    default Boolean succeeded() {
      return this instanceof ReadResult.Success;
    }
  }

  sealed public interface AppendResult {
    record Success(
      ExpectedRevision nextExpectedRevision, Position logPosition) implements AppendResult {
    }

    record StreamAlreadyExists(ExpectedRevision actual) implements AppendResult {
    }

    record Conflict(ExpectedRevision expected,
                    ExpectedRevision actual) implements AppendResult {
    }

    record UnexpectedFailure(Throwable t) implements AppendResult {
    }

    default Boolean succeeded() {
      return this instanceof Success;
    }
  }

  sealed public interface DeleteResult {
    record Success() implements DeleteResult {
    }

    record StreamDoesNotExist() implements DeleteResult {
    }

    record Conflict(ExpectedRevision expected,
                    ExpectedRevision actual) implements DeleteResult {
    }

    record UnexpectedFailure(Throwable t) implements DeleteResult {
    }

    default Boolean succeeded() {
      return this instanceof Success;
    }
  }
}
