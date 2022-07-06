package io.eventdriven.uniqueness.users;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface UserEvent {
  record UserRegistered(
    UUID userId,
    String email,
    OffsetDateTime registeredAt
  ) implements UserEvent {
  }

  record UserEmailChanged(
    UUID userId,
    String newEmail,
    OffsetDateTime changedAt
  ) implements UserEvent {
  }
}
