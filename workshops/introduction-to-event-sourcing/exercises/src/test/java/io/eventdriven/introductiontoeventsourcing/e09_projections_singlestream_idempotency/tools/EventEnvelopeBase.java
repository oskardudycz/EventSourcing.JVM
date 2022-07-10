package io.eventdriven.introductiontoeventsourcing.e09_projections_singlestream_idempotency.tools;

import java.util.UUID;

public sealed interface EventEnvelopeBase {
  Object data();

  EventMetadata metadata();

  record EventEnvelope<T>(
    T data,
    EventMetadata metadata
  ) implements EventEnvelopeBase {

  }

  record EventMetadata(
    String eventId,
    long streamPosition,
    long logPosition
  ) {
    public static EventMetadata of(long streamPosition, long logPosition) {
      return new EventMetadata(UUID.randomUUID().toString(), streamPosition, logPosition);
    }
  }
}
