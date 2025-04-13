package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccount.Status;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.*;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class GuestStayAccountDecider {
  public static GuestCheckedIn handle(GuestStayAccountCommand.CheckInGuest command) {
    return new GuestCheckedIn(command.guestStayId(), command.now());
  }

  public static ChargeRecorded handle(GuestStayAccountCommand.RecordCharge command, GuestStayAccount state) {
    if (state.status() != Status.OPEN)
      throw new RuntimeException("Cannot record charge if status is other than Open");

    return new ChargeRecorded(state.id(), command.amount(), command.now());
  }

  public static PaymentRecorded handle(GuestStayAccountCommand.RecordPayment command, GuestStayAccount state) {
    if (state.status() != Status.OPEN)
      throw new RuntimeException("Cannot record payment if status is other than Open");

    return new PaymentRecorded(state.id(), command.amount(), command.now());
  }

  public static GuestStayAccountEvent handle(GuestStayAccountCommand.CheckOutGuest command, GuestStayAccount state) {
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
      case GuestStayAccountCommand.CheckInGuest checkInGuest -> handle(checkInGuest);
      case GuestStayAccountCommand.RecordCharge recordCharge -> handle(recordCharge, state);
      case GuestStayAccountCommand.RecordPayment recordPayment -> handle(recordPayment, state);
      case GuestStayAccountCommand.CheckOutGuest checkOutGuest -> handle(checkOutGuest, state);
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
