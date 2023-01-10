package io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount;

import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface GuestStayAccountEvent {
  record GuestCheckedIn(
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
    double amount,
    OffsetDateTime recordedAt
  ) implements GuestStayAccountEvent {
  }

  record GuestCheckedOut(
    UUID guestStayAccountId,
    @Nullable
    UUID groupCheckoutId,
    OffsetDateTime completedAt
  ) implements GuestStayAccountEvent {
  }

  record GuestCheckoutFailed(
    UUID guestStayAccountId,
    Reason reason,
    @Nullable
    UUID groupCheckoutId,
    OffsetDateTime failedAt
  ) implements GuestStayAccountEvent {
    public enum Reason
    {
      InvalidState,
      BalanceNotSettled
    }
  }
}
