package io.eventdriven.buildyourowneventstore.e01_storage.mongodb.events;

public record EventMetadata(
  String eventId,
  String eventType,
  long streamPosition,
  String streamName
) {
}
