package io.eventdriven.eventstores.mongodb.events;

public record EventMetadata(
  String eventId,
  String eventType,
  long streamPosition,
  String streamName
) {
}
