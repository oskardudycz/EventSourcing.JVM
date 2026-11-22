package io.eventdriven.testing.messaging;

import io.eventdriven.core.messaging.EventMetadata;
import java.util.UUID;

public final class Metadata {
    public static EventMetadata at(String streamId, long position) {
        var id = UUID.randomUUID().toString();

        return new EventMetadata(streamId, position, position, 0, id, id, id, null, null);
    }
}
