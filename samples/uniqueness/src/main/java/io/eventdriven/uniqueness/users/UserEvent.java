package io.eventdriven.uniqueness.users;

import java.util.UUID;

public interface UserEvent {
  record UserRegistered(
    UUID userId,
    UUID email
  ) implements UserEvent {
  }

  record UserEmailChanged(
    UUID userId,
    UUID newEmail
  ) implements UserEvent {
  }

  record UserDataErasureRequested(
    UUID userId
  ) implements UserEvent {
  }
}
