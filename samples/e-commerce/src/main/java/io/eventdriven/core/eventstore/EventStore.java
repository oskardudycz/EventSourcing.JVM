package io.eventdriven.core.eventstore;

import io.eventdriven.core.messaging.EventEnvelope;
import java.util.List;
import java.util.function.Consumer;

public interface EventStore {
    ReadResult read(String streamId);

    AppendResult append(String streamId, Object... events);

    AppendResult append(String streamId, ExpectedStreamVersion expectedVersion, Object... events);

    Subscription subscribe(String name, List<Class<?>> types, Consumer<EventEnvelope<Object>> handler);

    sealed interface ReadResult {
        record Success(List<EventEnvelope<Object>> events) implements ReadResult {}

        record StreamDoesNotExist() implements ReadResult {}

        record UnexpectedFailure(Throwable t) implements ReadResult {}

        default Boolean succeeded() {
            return this instanceof ReadResult.Success;
        }
    }

    sealed interface AppendResult {
        record Success(long nextStreamVersion, Checkpoint lastEvent) implements AppendResult {}

        record StreamAlreadyExists(long actual) implements AppendResult {}

        record Conflict(ExpectedStreamVersion expected, long actual) implements AppendResult {}

        record UnexpectedFailure(Throwable t) implements AppendResult {}

        default Boolean succeeded() {
            return this instanceof Success;
        }
    }
}
