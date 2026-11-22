package io.eventdriven.core.messaging;

public record EventEnvelope<Event>(Event data, EventMetadata metadata) {}
