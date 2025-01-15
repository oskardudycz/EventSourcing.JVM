package io.eventdriven.buildyourowneventstore.e01_storage.mongodb;

import java.time.LocalDateTime;

public record StreamMetadata(
  String streamId,
  String streamType,
  Long streamPosition,
  LocalDateTime createdAt,
  LocalDateTime updatedAt
) {
}
