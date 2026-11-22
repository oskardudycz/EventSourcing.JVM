package io.eventdriven.testing.eventstore;

import io.eventdriven.core.eventstore.Checkpoint;
import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.ExpectedStreamVersion;
import io.eventdriven.core.eventstore.Subscription;
import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.EventMetadata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class InMemoryEventStore implements EventStore {
    private record Subscriber(List<Class<?>> types, Consumer<EventEnvelope<Object>> handler) {}

    private final Map<String, List<EventEnvelope<Object>>> streams = new HashMap<>();
    private final List<Subscriber> subscribers = new ArrayList<>();
    private long logPosition = -1;

    @Override
    public ReadResult read(String streamId) {
        var events = streams.get(streamId);

        return events == null ? new ReadResult.StreamDoesNotExist() : new ReadResult.Success(List.copyOf(events));
    }

    @Override
    public AppendResult append(String streamId, Object... events) {
        return append(streamId, ExpectedStreamVersion.any(), events);
    }

    @Override
    public AppendResult append(String streamId, ExpectedStreamVersion expectedVersion, Object... events) {
        var stream = streams.get(streamId);
        var actual = stream == null ? -1L : stream.size() - 1L;

        switch (expectedVersion) {
            case ExpectedStreamVersion.NoStream noStream
            when stream != null -> {
                return new AppendResult.StreamAlreadyExists(actual);
            }
            case ExpectedStreamVersion.StreamExists streamExists
            when stream == null -> {
                return new AppendResult.Conflict(expectedVersion, actual);
            }
            case ExpectedStreamVersion.At at
            when at.version() != actual -> {
                return new AppendResult.Conflict(expectedVersion, actual);
            }
            default -> {}
        }

        var appended = streams.computeIfAbsent(streamId, id -> new ArrayList<>());
        var newEvents = new ArrayList<EventEnvelope<Object>>();

        for (var event : events) {
            var messageId = UUID.randomUUID().toString();
            var metadata = new EventMetadata(
                    streamId, appended.size(), ++logPosition, 0, messageId, messageId, messageId, null, null);
            var envelope = new EventEnvelope<>(event, metadata);
            appended.add(envelope);
            newEvents.add(envelope);
        }

        for (var envelope : newEvents) {
            for (var subscriber : subscribers) {
                if (subscriber.types().stream().anyMatch(type -> type.isInstance(envelope.data())))
                    subscriber.handler().accept(envelope);
            }
        }

        return new AppendResult.Success(appended.size() - 1L, new Checkpoint(0, logPosition));
    }

    @Override
    public Subscription subscribe(String name, List<Class<?>> types, Consumer<EventEnvelope<Object>> handler) {
        subscribers.add(new Subscriber(types, handler));

        return (checkpoint, timeout) -> {};
    }
}
