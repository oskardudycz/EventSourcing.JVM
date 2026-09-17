package io.eventdriven.distributedprocesses.core.messaging;

public record EventMetadata(
  String streamId,
  long streamPosition
) {
}
