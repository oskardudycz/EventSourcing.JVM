package io.eventdriven.eventdrivenarchitecture.e06_consistency.core;

public record Versioned<T>(T document, int version) {
}
