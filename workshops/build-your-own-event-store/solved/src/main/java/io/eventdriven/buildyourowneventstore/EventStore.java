package io.eventdriven.buildyourowneventstore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public interface EventStore {
    void init();

    default <Stream> void appendEvents(
        Class<Stream> streamClass,
        UUID streamId,
        Object... events
    ) {
        appendEvents(streamClass, streamId, null, events);
    }

    <Stream> void appendEvents(
        Class<Stream> streamClass,
        UUID streamId,
        Long expectedVersion,
        Object... events
    );

    default List<Object> getEvents(
        UUID streamId
    ) {
        return getEvents(streamId, null, null);
    }

    List<Object> getEvents(
        UUID streamId,
        Long atStreamVersion,
        LocalDateTime atTimestamp
    );

    default <Stream, Event> Optional<Stream> aggregateStream(
        Supplier<Stream> getDefault,
        BiFunction<Stream, Event, Stream> evolve,
        UUID streamId
    ) {
        return aggregateStream(getDefault, evolve, streamId, null, null);
    }

    default <Stream, Event> Optional<Stream> aggregateStream(
        Supplier<Stream> getDefault,
        BiFunction<Stream, Event, Stream> evolve,
        UUID streamId,
        Long atStreamVersion,
        LocalDateTime atTimestamp
    ) {
        var events = getEvents(streamId, atStreamVersion, atTimestamp);

        if (events.isEmpty()) {
            return Optional.empty();
        }

        var aggregate = getDefault.get();

        for (var event : events) {
            aggregate = evolve.apply(aggregate, (Event) event);
        }

        return Optional.of(aggregate);
    }
}
