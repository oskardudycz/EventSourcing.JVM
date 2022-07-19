package io.eventdriven.uniqueness.core.resourcereservation.esdb;

import java.time.Duration;
import java.time.OffsetDateTime;

public sealed interface ResourceReservationEvent {
  public record ResourceReservationInitiated(
    String resourceKey,
    OffsetDateTime initiatedAt,
    Duration tentativeLockFor) implements ResourceReservationEvent {
  }

  public record ResourceReservationConfirmed(
    String resourceKey,
    OffsetDateTime reservedAt) implements ResourceReservationEvent {
  }

  public record ResourceReservationReleaseInitiated(
    String resourceKey,
    OffsetDateTime releasedAt) implements ResourceReservationEvent {
  }
}
