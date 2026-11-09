package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core;

import org.jspecify.annotations.Nullable;

public record RoutingTarget(String target, @Nullable String key) {
}
