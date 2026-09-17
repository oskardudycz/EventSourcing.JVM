package io.eventdriven.distributedprocesses.core.messaging;

public record EventEnvelope<Event>(
  Event data,
  EventMetadata metadata
) {
}
