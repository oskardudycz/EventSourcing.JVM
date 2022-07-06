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
      case UserRegistered registered -> handle(registered);
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
          repository.save(resource);
        }
      });
  }

  private void handle(UserEmailChanged emailChanged) {
    repository.findById(Hash.hash(emailChanged.newEmail()).toString())
      .ifPresent(resource -> {
        if (!resource.isConfirmed()) {
          resource.confirm(emailChanged.changedAt());
          repository.save(resource);
        }
      });
  }
}
