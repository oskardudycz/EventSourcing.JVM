package io.eventdriven.distributedprocesses.hotelmanagement.gueststayaccount;

import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface GuestStayAccountEvent {
  record OpenGuestStayAccount(
    UUID guestStayAccountId,
    OffsetDateTime openedAt
  ) implements GuestStayAccountEvent {
  }

  record ChargeRecorded(
    UUID guestStayAccountId,
    double amount,
    OffsetDateTime recordedAt
  ) implements GuestStayAccountEvent {
  }

  record PaymentRecorded(
    UUID guestStayAccountId,
    double amount
  ) implements GuestStayAccountEvent {
  }

  record GuestAccountCheckoutCompleted(
    UUID guestStayAccountId,
    @Nullable
    UUID groupCheckoutId,
    OffsetDateTime completedAt
  ) implements GuestStayAccountEvent {
  }

  record GuestAccountCheckoutFailed(
    UUID guestStayAccountId,
    @Nullable
    UUID groupCheckoutId,
    OffsetDateTime failedAt
  ) implements GuestStayAccountEvent {
  }
}
