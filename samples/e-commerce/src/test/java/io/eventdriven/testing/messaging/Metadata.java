package io.eventdriven.testing.messaging;

import io.eventdriven.core.messaging.MessageMetadata;
import io.eventdriven.core.messaging.MessageSource;
import java.time.Instant;
import java.util.UUID;

public final class Metadata {
    public static MessageMetadata at(String streamId, long position) {
        var id = UUID.randomUUID().toString();

        return new MessageMetadata(
                id, id, id, null, null, new MessageSource.InStream(streamId, position, position, 0, Instant.EPOCH));
    }
}
