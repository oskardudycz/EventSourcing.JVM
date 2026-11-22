package io.eventdriven.core.messaging;

import org.jspecify.annotations.Nullable;

public record MessageMetadata(
        String messageId,
        String correlationId,
        String causationId,
        @Nullable String traceId,
        @Nullable String spanId,
        MessageSource source) {

    public MessageSource.InStream stream() {
        if (source instanceof MessageSource.InStream stream) return stream;

        throw new IllegalStateException("Message '%s' came from %s, not from a stream".formatted(messageId, source));
    }
}
