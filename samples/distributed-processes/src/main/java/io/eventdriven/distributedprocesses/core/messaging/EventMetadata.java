package io.eventdriven.distributedprocesses.core.messaging;

import org.jspecify.annotations.Nullable;

public record EventMetadata(
        String streamId,
        long streamPosition,
        long logPosition,
        String messageId,
        String correlationId,
        String causationId,
        @Nullable String traceId,
        @Nullable String spanId) {}
