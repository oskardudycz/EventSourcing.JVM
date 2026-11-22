package io.eventdriven.core.eventstore;

import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.processing.MessageProcessor;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

public interface EventStore {
    ReadResult read(String streamId);

    AppendResult append(String streamId, Object... events);

    AppendResult append(String streamId, ExpectedStreamVersion expectedVersion, Object... events);

    Subscription subscribe(String name, List<Class<?>> types, Consumer<MessageEnvelope<Object>> handler);

    default Subscription subscribe(String name, MessageProcessor... processors) {
        return subscribe(name, MessageProcessor.canHandle(processors), MessageProcessor.handle(processors));
    }

    /**
     * Waits until every subscription has handled the events of its types that this store appended before the call,
     * and the events that subscriptions appended while it waited.
     * Fails when the wait takes longer than the timeout.
     */
    void whenCaughtUp(Duration timeout);

    default void whenCaughtUp() {
        whenCaughtUp(Duration.ofSeconds(30));
    }

    sealed interface ReadResult {
        record Success(List<MessageEnvelope<Object>> events) implements ReadResult {}

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
