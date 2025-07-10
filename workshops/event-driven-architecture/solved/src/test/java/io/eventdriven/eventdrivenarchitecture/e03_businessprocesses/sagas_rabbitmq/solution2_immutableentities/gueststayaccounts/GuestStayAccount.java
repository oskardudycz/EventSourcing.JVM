package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.*;
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

