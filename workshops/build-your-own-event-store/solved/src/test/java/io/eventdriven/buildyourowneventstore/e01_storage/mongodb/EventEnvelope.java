package io.eventdriven.buildyourowneventstore.e01_storage.mongodb;

public record EventEnvelope<Event>(
  String type,
  Event data,
  EventMetadata metadata
) {
  public static <Event> EventEnvelope<Event> of(
    final Class<Event> type,
    Event data,
    EventMetadata metadata
  ) {
    return new EventEnvelope<>(type.getName(), data, metadata);
  }
}
