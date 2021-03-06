package io.eventdriven.uniqueness.users.reservation;

import io.eventdriven.uniqueness.core.esdb.EventStore;
import io.eventdriven.uniqueness.core.resourcereservation.Hash;
import io.eventdriven.uniqueness.core.resourcereservation.ResourceReservationHandler;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.uniqueness.users.UserEvent.*;

public class UserCommandHandler {
  private final ResourceReservationHandler resourceReservationHandler;
  private final EventStore eventStore;

  public UserCommandHandler(ResourceReservationHandler resourceReservationHandler, EventStore eventStore) {
    this.resourceReservationHandler = resourceReservationHandler;
    this.eventStore = eventStore;
  }

  public Boolean registerUser(UUID userId, String email) {
    var resourceKey = Hash.hash(email).toString();

    return resourceReservationHandler.reserve(resourceKey, ack -> {
      var userRegistered = new UserRegistered(userId, email, OffsetDateTime.now());

      var result = eventStore.append("user-%s".formatted(userId), userRegistered);

      if (result.succeeded()) {
        ack.accept(true);
      }
    });
  }
}
