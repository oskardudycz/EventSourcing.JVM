package io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccount.Status;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckInGuest;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.RecordCharge;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.RecordPayment;
import io.eventdriven.eventdrivenarchitecture.e05_optimistic_concurrency.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class GuestStayAccountDecider {
  public static List<GuestStayAccountEvent> handle(CheckInGuest command) {
    return List.of(new GuestCheckedIn(command.guestStayId(), command.now()));
  }

  public static List<GuestStayAccountEvent> handle(RecordCharge command, GuestStayAccount state) {
    if (state.status() != Status.OPEN)
      throw new RuntimeException("Cannot record charge if status is other than Open");

    if (state.recordedChargeIds().contains(command.chargeId()))
      return List.of();

    return List.of(new ChargeRecorded(state.id(), command.chargeId(), command.amount(), command.now()));
  }

  public static List<GuestStayAccountEvent> handle(RecordPayment command, GuestStayAccount state) {
    if (state.status() != Status.OPEN)
      throw new RuntimeException("Cannot record payment if status is other than Open");

    if (state.recordedPaymentIds().contains(command.paymentId()))
      return List.of();

    return List.of(new PaymentRecorded(state.id(), command.paymentId(), command.amount(), command.now()));
  }

  public static List<GuestStayAccountEvent> handle(CheckOutGuest command, GuestStayAccount state) {
    if (state.status() == Status.CHECKED_OUT && Objects.equals(state.groupCheckoutId(), command.groupCheckOutId()))
      return List.of();

    if (state.status() != Status.OPEN || !state.isSettled()) {
      return List.of(new GuestCheckoutFailed(
        state.id(),
        !state.isSettled() ? GuestCheckoutFailed.Reason.BALANCE_NOT_SETTLED : GuestCheckoutFailed.Reason.INVALID_STATE,
        command.now(),
        command.groupCheckOutId()
      ));
    }
    return List.of(new GuestCheckedOut(
      state.id(),
      command.now(),
      command.groupCheckOutId()
    ));
  }

  public static List<GuestStayAccountEvent> decide(GuestStayAccountCommand command, GuestStayAccount state) {
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
      UUID chargeId,
      double amount,
      OffsetDateTime now
    ) implements GuestStayAccountCommand {
    }

    record RecordPayment(
      UUID guestStayId,
      UUID paymentId,
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
