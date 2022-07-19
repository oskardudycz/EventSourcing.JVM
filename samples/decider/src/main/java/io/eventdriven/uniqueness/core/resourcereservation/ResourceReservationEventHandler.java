package io.eventdriven.uniqueness.core.resourcereservation;

import io.eventdriven.uniqueness.core.resourcereservation.jpa.ResourceReservation;
import io.eventdriven.uniqueness.core.resourcereservation.jpa.ResourceReservationRepository;

import static io.eventdriven.uniqueness.core.resourcereservation.esdb.ResourceReservationEvent.*;

// This class is used to maintain read model used to ensure that reservation is released
public class ResourceReservationEventHandler {
  private final ResourceReservationRepository repository;
  private final ResourceReservationHandler reservationHandler;

  public ResourceReservationEventHandler(ResourceReservationRepository repository, ResourceReservationHandler reservationHandler) {
    this.repository = repository;
    this.reservationHandler = reservationHandler;
  }

  public void handle(Object event){
    switch (event) {
      case ResourceReservationInitiated initiated -> handle(initiated);
      case ResourceReservationConfirmed confirmed -> handle(confirmed);
      case ResourceReservationReleaseInitiated releaseInitiated -> handle(releaseInitiated);
      default -> { }
    }
  }

  private void handle(ResourceReservationInitiated reservationInitiated) {
    repository.save(new ResourceReservation(
      reservationInitiated.resourceKey(),
      reservationInitiated.initiatedAt().plus(reservationInitiated.tentativeLockFor()),
      ResourceReservation.Status.Pending,
      reservationInitiated.initiatedAt(),
      null
    ));
  }

  private void handle(ResourceReservationConfirmed reservationConfirmed) {
    repository.findById(reservationConfirmed.resourceKey())
      .ifPresent(reservation -> {
        reservation.confirm(reservationConfirmed.reservedAt());

        repository.save(reservation);
      });
  }

  private void handle(ResourceReservationReleaseInitiated reservationReleased) {
    var resourceKey = reservationReleased.resourceKey();

    reservationHandler.release(resourceKey);

    if (repository.existsById(resourceKey)) {
      repository.deleteById(resourceKey);
    }
  }
}
