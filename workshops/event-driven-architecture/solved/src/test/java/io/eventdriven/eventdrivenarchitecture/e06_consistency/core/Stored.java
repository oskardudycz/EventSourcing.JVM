package io.eventdriven.eventdrivenarchitecture.e06_consistency.core;

import java.util.UUID;

public record Stored<T>(UUID id, T document, int version, long position) {
}
