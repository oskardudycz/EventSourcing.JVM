package io.eventdriven.ecommerce.core.events;

public record EventMetadata(
  String EventId,
  long StreamPosition,
  long LogPosition
) {
}
