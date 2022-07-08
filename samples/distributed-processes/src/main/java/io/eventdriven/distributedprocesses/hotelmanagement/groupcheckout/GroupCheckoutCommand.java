package io.eventdriven.distributedprocesses.hotelmanagement.groupcheckout;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface GroupCheckoutCommand {
  record InitiateGroupCheckout(
    UUID groupCheckoutId,
    UUID clerkId,
    UUID[] guestStayAccountIds
  ) implements GroupCheckoutCommand {
  }

  record RecordGuestStayInitiation(
    UUID groupCheckoutId,
    UUID[] guestStayAccountIds
  ) implements GroupCheckoutCommand {
  }

  record RecordGuestCheckoutCompletion(
    UUID groupCheckoutId,
    UUID guestStayAccountId,
    OffsetDateTime completedAt
  ) implements GroupCheckoutCommand {
  }

  record RecordGuestCheckoutFailure(
    UUID groupCheckoutId,
    UUID guestStayAccountId,
    OffsetDateTime failedAt
  ) implements GroupCheckoutCommand {
  }

  record CompleteGroupCheckout(
    UUID groupCheckoutId,
    UUID[] completedCheckouts,
    UUID[] failedCheckouts
  ) implements GroupCheckoutCommand {
  }

  record FailGroupCheckout(
    UUID groupCheckoutId,
    UUID[] completedCheckouts,
    UUID[] failedCheckouts,
    OffsetDateTime failedAt
  ) implements GroupCheckoutCommand {
  }
}
