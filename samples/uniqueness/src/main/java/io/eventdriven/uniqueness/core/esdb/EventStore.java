package io.eventdriven.uniqueness.core.esdb;

import com.eventstore.dbclient.*;
import io.eventdriven.uniqueness.core.serialization.EventSerializer;

import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class EventStore {
  public AppendResult append(String streamId, Object... events) {
    var eventsToAppend = Arrays.stream(events)
      .map(EventSerializer::serialize)
      .toList();

    try {
      var result = eventStore.appendToStream(
        streamId,
        AppendToStreamOptions.get().expectedRevision(ExpectedRevision.NO_STREAM),
        eventsToAppend.iterator()
      ).get();

      return new AppendResult.Success(result.getNextExpectedRevision());
    } catch (WrongExpectedVersionException e) {
      return new AppendResult.StreamAlreadyExists();
    } catch (InterruptedException | ExecutionException e) {
      return new AppendResult.UnexpectedFailure(e);
    }
  }

  public AppendResult append(String streamId, StreamRevision expectedRevision, Object... events) {
    try {
      var eventsToAppend = Arrays.stream(events)
        .map(EventSerializer::serialize)
        .toList();

      var result = eventStore.appendToStream(
        streamId,
        AppendToStreamOptions.get().expectedRevision(expectedRevision),
        eventsToAppend.iterator()
      ).get();

      return new AppendResult.Success(result.getNextExpectedRevision());
    } catch (WrongExpectedVersionException e) {
      return new AppendResult.Conflict(expectedRevision, e.getActualVersion());
    } catch (InterruptedException | ExecutionException e) {
      return new AppendResult.UnexpectedFailure(e);
    }
  }

  public DeleteResult deleteStream(String streamId) {
    try {
      eventStore.deleteStream(
        streamId,
        DeleteStreamOptions.get().expectedRevision(ExpectedRevision.STREAM_EXISTS)
      ).get();

      return new DeleteResult.Success();
    } catch (WrongExpectedVersionException e) {
      return new DeleteResult.StreamDoesNotExist();
    } catch (InterruptedException | ExecutionException e) {
      return new DeleteResult.UnexpectedFailure(e);
    }
  }

  public DeleteResult deleteStream(String streamId, StreamRevision expectedRevision) {
    try {
      eventStore.deleteStream(
        streamId,
        DeleteStreamOptions.get().expectedRevision(expectedRevision)
      ).get();

      return new DeleteResult.Success();
    } catch (WrongExpectedVersionException e) {
      return new DeleteResult.Conflict(expectedRevision, e.getActualVersion());
    } catch (InterruptedException | ExecutionException e) {
      return new DeleteResult.UnexpectedFailure(e);
    }
  }

  private final EventStoreDBClient eventStore;

  public EventStore(EventStoreDBClient eventStoreDBClient) {
    this.eventStore = eventStoreDBClient;
  }

  sealed interface AppendResult {
    record Success(
      StreamRevision nextExpectedRevision) implements AppendResult {
    }

    record StreamAlreadyExists() implements AppendResult {
    }

    record Conflict(StreamRevision expected,
                    StreamRevision actual) implements AppendResult {
    }

    record UnexpectedFailure(Throwable t) implements AppendResult {
    }

    default Boolean succeeded() {
      return this instanceof Success;
    }
  }

  sealed interface DeleteResult {
    record Success() implements DeleteResult {
    }

    record StreamDoesNotExist() implements DeleteResult {
    }

    record Conflict(StreamRevision expected,
                    StreamRevision actual) implements DeleteResult {
    }

    record UnexpectedFailure(Throwable t) implements DeleteResult {
    }

    default Boolean succeeded() {
      return this instanceof Success;
    }
  }
}
