package io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface GroupCheckoutCommand {
  record InitiateGroupCheckout(
    UUID groupCheckoutId,
    UUID clerkId,
    UUID[] guestStayAccountIds,
    OffsetDateTime now
  ) implements GroupCheckoutCommand {
  }

  record RecordGuestCheckoutsInitiation(
    UUID groupCheckoutId,
    UUID[] guestStayAccountIds,
    OffsetDateTime now
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
}
