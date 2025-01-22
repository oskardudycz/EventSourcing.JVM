package io.eventdriven.buildyourowneventstore.e04_event_store_methods.mongodb.stream_as_document.streams;

import java.time.LocalDateTime;

public record StreamMetadata(
  String streamId,
  String streamType,
  Long streamPosition,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) {
}
