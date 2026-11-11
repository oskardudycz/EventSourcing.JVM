package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.core;

public record Versioned<T>(T document, int version) {
}
