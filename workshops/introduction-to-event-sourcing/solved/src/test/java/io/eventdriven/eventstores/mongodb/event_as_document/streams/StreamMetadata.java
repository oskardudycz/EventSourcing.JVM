package io.eventdriven.eventstores.mongodb.event_as_document.streams;

import java.time.LocalDateTime;

public record StreamMetadata(
  String streamId,
  String streamType,
  Long streamPosition,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) {
}
