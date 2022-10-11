package io.eventdriven.buildyourowneventstore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface EventStore {
    void init();

    default <TStream> void appendEvents(
        Class<TStream> streamClass,
        UUID streamId,
        Object... events
    ){
        appendEvents(streamClass, streamId, null, events);
    }

    <TStream> void appendEvents(
        Class<TStream> streamClass,
        UUID streamId,
        Long expectedVersion,
        Object... events
    );

    default List<Object> getEvents(
        UUID streamId
    ){
        return getEvents(streamId, null, null);
    }

    List<Object> getEvents(
        UUID streamId,
        Long atStreamVersion,
        LocalDateTime atTimestamp
    );
}
