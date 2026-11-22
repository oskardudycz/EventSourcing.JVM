package io.eventdriven.core.messaging;

import io.eventdriven.core.eventstore.Checkpoint;
import org.jspecify.annotations.Nullable;

public record EventMetadata(
        String streamId,
        long streamPosition,
        long logPosition,
        long transactionId,
        String messageId,
        String correlationId,
        String causationId,
        @Nullable String traceId,
        @Nullable String spanId) {

    public Checkpoint checkpoint() {
        return new Checkpoint(transactionId, logPosition);
    }
}
