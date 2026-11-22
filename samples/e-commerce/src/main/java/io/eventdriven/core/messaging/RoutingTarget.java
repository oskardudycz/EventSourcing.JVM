package io.eventdriven.core.messaging;

import org.jspecify.annotations.Nullable;

public record RoutingTarget(
        @Nullable String target, @Nullable String key) {}
