package io.eventdriven.distributedprocesses.core.kurrentdb;

import io.kurrent.dbclient.StreamState;
import io.kurrent.dbclient.Position;
import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;

import java.util.function.Consumer;

public interface EventStore {
  ReadResult read(String streamId);

  AppendResult append(String streamId, Object... events);

  AppendResult append(String streamId, StreamState expectedRevision, Object... events);

  <Event> EventStore subscribe(Class<Event> type, Consumer<EventEnvelope<Event>> handler);

  sealed interface ReadResult {
    record Success(
      Object[] events
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

  sealed interface AppendResult {
    record Success(
      StreamState nextExpectedRevision, Position logPosition) implements AppendResult {
    }

    record StreamAlreadyExists(StreamState actual) implements AppendResult {
    }

    record Conflict(StreamState expected,
                    StreamState actual) implements AppendResult {
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

    record Conflict(StreamState expected,
                    StreamState actual) implements DeleteResult {
    }

    record UnexpectedFailure(Throwable t) implements DeleteResult {
    }

    default Boolean succeeded() {
      return this instanceof Success;
    }
  }
}
