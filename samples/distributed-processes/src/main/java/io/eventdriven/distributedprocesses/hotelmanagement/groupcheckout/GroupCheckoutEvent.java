package io.eventdriven.distributedprocesses.hotelmanagement.groupcheckout;

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

  record GuestCheckoutsInitiated(
    UUID groupCheckoutId,
    UUID[] guestStayAccountIds,
    OffsetDateTime initiatedAt
  ) implements GroupCheckoutEvent {
  }

  record GuestCheckoutCompleted(
    UUID groupCheckoutId,
    UUID guestStayAccountId,
    OffsetDateTime completedAt
  ) implements GroupCheckoutEvent {
  }

  record GuestCheckoutFailed(
    UUID groupCheckoutId,
    UUID guestStayAccountId,
    OffsetDateTime failedAt
  ) implements GroupCheckoutEvent {
  }

  record GroupCheckoutCompleted(
    UUID groupCheckoutId,
    UUID[] completedCheckouts,
    UUID[] failedCheckouts,
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
