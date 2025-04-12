package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.*;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

public record GuestStayAccount(
  UUID id,
  double balance,
  Status status
) {
  public enum Status {
    OPEN,
    CHECKED_OUT
  }

  public boolean isSettled() {
    return balance == 0;
  }

  public static GuestCheckedIn checkIn(UUID guestStayId, OffsetDateTime now) {
    return new GuestCheckedIn(guestStayId, now);
  }

  public ChargeRecorded recordCharge(double amount, OffsetDateTime now) {
    if (status != Status.OPEN)
      throw new RuntimeException("Cannot record charge if status is other than Open");

    return new ChargeRecorded(id(), amount, now);
  }

  public PaymentRecorded recordPayment(double amount, OffsetDateTime now) {
    if (status != Status.OPEN)
      throw new RuntimeException("Cannot record payment if status is other than Open");

    return new PaymentRecorded(id(), amount, now);
  }

  public GuestStayAccountEvent checkout(OffsetDateTime now, @Nullable UUID groupCheckoutId) {
    if (status != Status.OPEN || balance != 0) {
      return new GuestCheckoutFailed(
        id(),
        balance != 0 ? GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED : GuestCheckoutFailed.Reason.INVALID_STATE,
        now,
        groupCheckoutId
      );
    }
    return new GuestCheckedOut(id(), now, groupCheckoutId);
  }

  public static GuestStayAccount evolve(GuestStayAccount state, GuestStayAccountEvent event) {
    return switch (event) {
      case GuestCheckedIn opened -> new GuestStayAccount(
        opened.guestStayAccountId(),
        0,
        Status.OPEN
      );
      case ChargeRecorded chargeRecorded -> new GuestStayAccount(
        state.id(),
        state.balance() - chargeRecorded.amount(),
        state.status()
      );
      case PaymentRecorded paymentRecorded -> new GuestStayAccount(
        state.id(),
        state.balance() + paymentRecorded.amount(),
        state.status()
      );
      case GuestCheckedOut ignored -> new GuestStayAccount(
        state.id(),
        state.balance(),
        Status.CHECKED_OUT
      );
      case GuestCheckoutFailed ignored -> state;
    };
  }

  public static final GuestStayAccount INITIAL = new GuestStayAccount(
    null,
    Double.MIN_VALUE,
    null
  );
}

