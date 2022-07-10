package io.eventdriven.introductiontoeventsourcing.solved.e08_projections_singlestream.tools;

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
