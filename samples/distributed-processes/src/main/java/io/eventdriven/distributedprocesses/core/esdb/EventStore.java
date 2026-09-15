package io.eventdriven.distributedprocesses.core.esdb;

import com.eventstore.dbclient.ExpectedRevision;
import com.eventstore.dbclient.Position;

public interface EventStore {
  ReadResult read(String streamId);

  AppendResult append(String streamId, Object... events);

  AppendResult append(String streamId, ExpectedRevision expectedRevision, Object... events);

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

  sealed interface DeleteResult {
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
