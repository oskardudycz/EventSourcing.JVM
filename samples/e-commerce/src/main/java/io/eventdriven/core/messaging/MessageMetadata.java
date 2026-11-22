package io.eventdriven.core.messaging;

import org.jspecify.annotations.Nullable;

public record MessageMetadata(
        String messageId,
        String correlationId,
        String causationId,
        @Nullable String traceId,
        @Nullable String spanId,
        MessageSource source) {

    public static MessageMetadata of(MessageValues values, MessageSource source) {
        return new MessageMetadata(
                values.messageId(),
                values.correlationId(),
                values.causationId(),
                values.traceId(),
                values.spanId(),
                source);
    }

    public MessageSource.InStream stream() {
        if (source instanceof MessageSource.InStream stream) return stream;

        throw new IllegalStateException("Message '%s' came from %s, not from a stream".formatted(messageId, source));
    }
}
