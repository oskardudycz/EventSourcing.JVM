package io.eventdriven.ecommerce.core.events;

public record EventMetadata(
  String eventId,
  long streamPosition,
  long logPosition,
  String eventType
) {
}
