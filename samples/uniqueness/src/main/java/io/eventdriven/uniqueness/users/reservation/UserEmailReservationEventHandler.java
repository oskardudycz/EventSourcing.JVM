package io.eventdriven.uniqueness.users.reservation;

import io.eventdriven.uniqueness.core.resourcereservation.Hash;
import io.eventdriven.uniqueness.core.resourcereservation.jpa.ResourceReservationRepository;

import static io.eventdriven.uniqueness.users.UserEvent.*;

public class UserEmailReservationEventHandler {
  ResourceReservationRepository repository;

  public UserEmailReservationEventHandler(ResourceReservationRepository repository) {
    this.repository = repository;
  }

  public void handle(Object event) {
    switch (event) {
      case UserRegistered initiated -> handle(initiated);
      case UserEmailChanged emailChanged -> handle(emailChanged);
      default -> {
      }
    }
  }

  private void handle(UserRegistered userRegistered) {
    repository.findById(Hash.hash(userRegistered.email()).toString())
      .ifPresent(resource -> {
        if (!resource.isConfirmed()) {
          resource.confirm(userRegistered.registeredAt());
        }
      });
  }

  private void handle(UserEmailChanged emailChanged) {
    repository.findById(Hash.hash(emailChanged.newEmail()).toString())
      .ifPresent(resource -> {
        if (!resource.isConfirmed()) {
          resource.confirm(emailChanged.changedAt());
        }
      });
  }
}
