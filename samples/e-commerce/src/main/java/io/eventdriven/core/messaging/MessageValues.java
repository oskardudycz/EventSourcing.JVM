package io.eventdriven.core.messaging;

import org.jspecify.annotations.Nullable;

public record MessageValues(
        String messageId,
        String correlationId,
        String causationId,
        @Nullable String traceId,
        @Nullable String spanId) {

    public MessageValues withMessageId(String messageId) {
        return new MessageValues(messageId, correlationId, causationId, traceId, spanId);
    }
}
