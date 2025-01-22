package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document.events;

public record EventMetadata(
  String eventId,
  String eventType,
  long streamPosition,
  String streamName
) {
}
