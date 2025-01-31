package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.events;

public record EventMetadata(
  String eventId,
  String eventType,
  long streamPosition,
  String streamName
) {
}
