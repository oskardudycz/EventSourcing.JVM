package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccount.Status;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckInGuest;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.RecordCharge;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.RecordPayment;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountEvent.*;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class GuestStayAccountDecider {
  public static GuestCheckedIn handle(CheckInGuest command) {
    return new GuestCheckedIn(command.guestStayId(), command.now());
  }

  public static ChargeRecorded handle(RecordCharge command, GuestStayAccount state) {
    if (state.status() != Status.OPEN)
      throw new RuntimeException("Cannot record charge if status is other than Open");

    return new ChargeRecorded(state.id(), command.amount(), command.now());
  }

  public static PaymentRecorded handle(RecordPayment command, GuestStayAccount state) {
    if (state.status() != Status.OPEN)
      throw new RuntimeException("Cannot record payment if status is other than Open");

    return new PaymentRecorded(state.id(), command.amount(), command.now());
  }

  public static GuestStayAccountEvent handle(CheckOutGuest command, GuestStayAccount state) {
    if (state.status() != Status.OPEN || !state.isSettled()) {
      return new GuestCheckoutFailed(
        state.id(),
        !state.isSettled() ? GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED : GuestCheckoutFailed.Reason.INVALID_STATE,
        command.now(),
        command.groupCheckOutId()
      );
    }
    return new GuestCheckedOut(
      state.id(),
      command.now(),
      command.groupCheckOutId()
    );
  }

  public static GuestStayAccountEvent decide(GuestStayAccountCommand command, GuestStayAccount state) {
    return switch (command) {
      case CheckInGuest checkInGuest -> handle(checkInGuest);
      case RecordCharge recordCharge -> handle(recordCharge, state);
      case RecordPayment recordPayment -> handle(recordPayment, state);
      case CheckOutGuest checkOutGuest -> handle(checkOutGuest, state);
    };
  }

  public sealed interface GuestStayAccountCommand {
    record CheckInGuest(
      UUID guestStayId,
      OffsetDateTime now
    ) implements GuestStayAccountCommand {
    }

    record RecordCharge(
      UUID guestStayId,
      double amount,
      OffsetDateTime now
    ) implements GuestStayAccountCommand {
    }

    record RecordPayment(
      UUID guestStayId,
      double amount,
      OffsetDateTime now
    ) implements GuestStayAccountCommand {
    }

    record CheckOutGuest(
      UUID guestStayId,
      OffsetDateTime now,
      UUID groupCheckOutId
    ) implements GuestStayAccountCommand {
      public CheckOutGuest(UUID guestStayId, OffsetDateTime now) {
        this(guestStayId, now, null);
      }
    }
  }
}
