package io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount;

import static io.eventdriven.distributedprocesses.core.collections.CollectionsExtensions.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccount.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountEvent.*;

public final class GuestStayAccountDecider {
  public static GuestStayAccountEvent[] handle(
      GuestStayAccountCommand command, GuestStayAccount state) {
    return switch (command) {
      case CheckInGuest checkIn: {
        if (!(state instanceof Initial initial))
          throw new IllegalStateException("Guest already checked in");

        yield toArray(handle(checkIn, initial));
      }
      case RecordCharge recordCharge: {
        if (!(state instanceof CheckedIn checkedIn))
          throw new IllegalStateException("Guest is not checked in");

        yield toArray(handle(recordCharge, checkedIn));
      }
      case RecordPayment recordPayment: {
        if (!(state instanceof CheckedIn checkedIn))
          throw new IllegalStateException("Guest is not checked in");

        yield toArray(handle(recordPayment, checkedIn));
      }
      case CheckOutGuest checkOut: {
        if (!(state instanceof CheckedIn checkedIn))
          yield toArray(new GuestCheckoutFailed(
              checkOut.guestStayAccountId(),
              GuestCheckoutFailed.Reason.InvalidState,
              checkOut.groupCheckoutId(),
              checkOut.now()));

        yield toArray(handle(checkOut, checkedIn));
      }
    };
  }

  private static GuestCheckedIn handle(CheckInGuest command, Initial ignore) {
    return new GuestCheckedIn(command.guestStayAccountId(), command.now());
  }

  private static ChargeRecorded handle(RecordCharge command, CheckedIn state) {
    return new ChargeRecorded(state.id(), command.amount(), command.now());
  }

  private static PaymentRecorded handle(RecordPayment command, CheckedIn state) {
    return new PaymentRecorded(state.id(), command.amount(), command.now());
  }

  private static GuestStayAccountEvent handle(CheckOutGuest command, CheckedIn state) {
    if (state.balance() != 0) {
      return new GuestCheckoutFailed(
          state.id(),
          GuestCheckoutFailed.Reason.BalanceNotSettled,
          command.groupCheckoutId(),
          command.now());
    }

    return new GuestCheckedIn(command.guestStayAccountId(), command.now());
  }
}
