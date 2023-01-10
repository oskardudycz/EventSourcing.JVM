package io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount;

import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface GuestStayAccountCommand {
  record CheckInGuest(
    UUID guestStayAccountId,
    OffsetDateTime now
  ) implements GuestStayAccountCommand {
  }

  record RecordCharge(
    UUID guestStayAccountId,
    double amount,
    OffsetDateTime now
  ) implements GuestStayAccountCommand {
  }

  record RecordPayment(
    UUID guestStayAccountId,
    double amount,
    OffsetDateTime now
  ) implements GuestStayAccountCommand {
  }

  record CheckOutGuest(
    UUID guestStayAccountId,
    @Nullable
    UUID groupCheckoutId,
    OffsetDateTime now
  ) implements GuestStayAccountCommand {
  }
}
