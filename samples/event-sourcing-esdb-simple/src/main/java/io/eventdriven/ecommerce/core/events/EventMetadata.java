package io.eventdriven.ecommerce.core.events;

public record EventMetadata(
  String EventId,
  long streamPosition,
  long logPosition,
  String eventType
) {
}
