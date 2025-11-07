package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.groupcheckouts;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface GroupCheckoutEvent {
  record GroupCheckoutInitiated(
    UUID groupCheckoutId,
    UUID clerkId,
    UUID[] guestStayAccountIds,
    OffsetDateTime initiatedAt
  ) implements GroupCheckoutEvent {
  }

  record GuestCheckoutCompletionRecorded(
    UUID groupCheckoutId,
    UUID guestStayAccountId,
    OffsetDateTime completedAt
  ) implements GroupCheckoutEvent {
  }

  record GuestCheckoutFailureRecorded(
    UUID groupCheckoutId,
    UUID guestStayAccountId,
    OffsetDateTime failedAt
  ) implements GroupCheckoutEvent {
  }

  record GroupCheckoutCompleted(
    UUID groupCheckoutId,
    UUID[] completedCheckouts,
    OffsetDateTime completedAt
  ) implements GroupCheckoutEvent {
  }

  record GroupCheckoutFailed(
    UUID groupCheckoutId,
    UUID[] completedCheckouts,
    UUID[] failedCheckouts,
    OffsetDateTime failedAt
  ) implements GroupCheckoutEvent {
  }
}
