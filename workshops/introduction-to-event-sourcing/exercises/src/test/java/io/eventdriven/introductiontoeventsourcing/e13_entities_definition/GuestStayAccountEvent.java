package io.eventdriven.introductiontoeventsourcing.e13_entities_definition;

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
    OffsetDateTime completedAt,
    @Nullable
    UUID groupCheckoutId
  ) implements GuestStayAccountEvent {
  }

  record GuestCheckoutFailed(
    UUID guestStayAccountId,
    Reason reason,
    OffsetDateTime failedAt,
    @Nullable
    UUID groupCheckoutId
  ) implements GuestStayAccountEvent {
    public enum Reason {
      INVALID_STATE,
      BALANCE_NOT_SETTLED
    }
  }
}
