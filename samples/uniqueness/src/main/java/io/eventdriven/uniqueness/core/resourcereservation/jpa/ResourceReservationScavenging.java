package io.eventdriven.uniqueness.core.resourcereservation.jpa;

import io.eventdriven.uniqueness.core.resourcereservation.Hash;
import io.eventdriven.uniqueness.core.resourcereservation.ResourceReservationHandler;

import java.time.OffsetDateTime;

// this could be called in the cron job to clean reserved resources
public class ResourceReservationScavenging {
  private final ResourceReservationRepository repository;
  private final ResourceReservationHandler resourceReservationHandler;

  public ResourceReservationScavenging(
    ResourceReservationRepository repository,
    ResourceReservationHandler resourceReservationHandler
  ) {
    this.repository = repository;
    this.resourceReservationHandler = resourceReservationHandler;
  }

  public void scavengeTimedOut(OffsetDateTime dateTime) {
    var timedOutReservations = repository.getTimedOut(dateTime);

    for (var reservation: timedOutReservations) {
      resourceReservationHandler.release(Hash.hash(reservation.getResourceKey()).toString());
    }
  }
}
