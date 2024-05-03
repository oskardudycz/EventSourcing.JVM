package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.core.events;

public record EventMetadata(
  String eventId,
  long streamPosition,
  long logPosition,
  String eventType
) {
}
