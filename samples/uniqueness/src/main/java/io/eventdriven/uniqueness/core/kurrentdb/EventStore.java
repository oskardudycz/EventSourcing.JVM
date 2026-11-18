package io.eventdriven.uniqueness.core.kurrentdb;

import io.eventdriven.uniqueness.core.serialization.EventSerializer;
import io.kurrent.dbclient.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

public class EventStore {
    public ReadResult read(String streamId) {
        try {
            var result =
                    eventStore.readStream(streamId, ReadStreamOptions.get()).get();

            return new ReadResult.Success(result.getEvents().toArray(new ResolvedEvent[0]));
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof StreamNotFoundException) {
                return new ReadResult.StreamDoesNotExist();
            }
            return new ReadResult.UnexpectedFailure(e);
        }
    }

    public AppendResult append(String streamId, Object... events) {
        var eventsToAppend =
                Arrays.stream(events).map(EventSerializer::serialize).toList();

        try {
            var result = eventStore
                    .appendToStream(
                            streamId,
                            AppendToStreamOptions.get().streamState(StreamState.noStream()),
                            eventsToAppend.iterator())
                    .get();

            return new AppendResult.Success(result.getNextExpectedRevision(), result.getLogPosition());
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof WrongExpectedVersionException wrongExpectedVersionException) {
                return new AppendResult.StreamAlreadyExists(wrongExpectedVersionException.getActualState());
            }

            return new AppendResult.UnexpectedFailure(e);
        }
    }

    public AppendResult append(String streamId, StreamState expectedRevision, Object... events) {
        try {
            var eventsToAppend =
                    Arrays.stream(events).map(EventSerializer::serialize).toList();

            var result = eventStore
                    .appendToStream(
                            streamId,
                            AppendToStreamOptions.get().streamState(expectedRevision),
                            eventsToAppend.iterator())
                    .get();

            return new AppendResult.Success(result.getNextExpectedRevision(), result.getLogPosition());
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof WrongExpectedVersionException wrongExpectedVersionException) {
                return new AppendResult.Conflict(expectedRevision, wrongExpectedVersionException.getActualState());
            }
            return new AppendResult.UnexpectedFailure(e);
        }
    }

    public DeleteResult deleteStream(String streamId) {
        try {
            eventStore
                    .deleteStream(streamId, DeleteStreamOptions.get().streamState(StreamState.streamExists()))
                    .get();

            return new DeleteResult.Success();
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof WrongExpectedVersionException) {
                return new DeleteResult.StreamDoesNotExist();
            }
            return new DeleteResult.UnexpectedFailure(e);
        }
    }

    public DeleteResult deleteStream(String streamId, StreamState expectedRevision) {
        try {
            eventStore
                    .deleteStream(streamId, DeleteStreamOptions.get().streamState(expectedRevision))
                    .get();

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

            var result = eventStore
                    .setStreamMetadata(
                            streamId, AppendToStreamOptions.get().streamState(StreamState.noStream()), metadata)
                    .get();

            return new AppendResult.Success(result.getNextExpectedRevision(), result.getLogPosition());
        } catch (InterruptedException | ExecutionException e) {
            if (e.getCause() instanceof WrongExpectedVersionException wrongExpectedVersionException) {
                return new AppendResult.StreamAlreadyExists(wrongExpectedVersionException.getActualState());
            }

            return new AppendResult.UnexpectedFailure(e);
        }
    }

    private final KurrentDBClient eventStore;

    public EventStore(KurrentDBClient kurrentDBClient) {
        this.eventStore = kurrentDBClient;
    }

    public sealed interface ReadResult {
        record Success(ResolvedEvent[] events) implements ReadResult {}

        record NoEventsFound() implements ReadResult {}

        record StreamDoesNotExist() implements ReadResult {}

        record UnexpectedFailure(Throwable t) implements ReadResult {}

        default Boolean succeeded() {
            return this instanceof ReadResult.Success;
        }
    }

    public sealed interface AppendResult {
        record Success(StreamState nextExpectedRevision, Position logPosition) implements AppendResult {}

        record StreamAlreadyExists(StreamState actual) implements AppendResult {}

        record Conflict(StreamState expected, StreamState actual) implements AppendResult {}

        record UnexpectedFailure(Throwable t) implements AppendResult {}

        default Boolean succeeded() {
            return this instanceof Success;
        }
    }

    public sealed interface DeleteResult {
        record Success() implements DeleteResult {}

        record StreamDoesNotExist() implements DeleteResult {}

        record Conflict(StreamState expected, StreamState actual) implements DeleteResult {}

        record UnexpectedFailure(Throwable t) implements DeleteResult {}

        default Boolean succeeded() {
            return this instanceof Success;
        }
    }
}
