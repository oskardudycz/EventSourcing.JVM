package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.streams;

import java.time.LocalDateTime;

public record StreamMetadata(
  String streamId,
  String streamType,
  Long streamPosition,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) {
}
