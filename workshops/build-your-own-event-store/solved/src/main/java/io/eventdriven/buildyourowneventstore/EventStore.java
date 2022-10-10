package io.eventdriven.buildyourowneventstore;

import java.util.UUID;

public interface EventStore {
    void Init();

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
}
