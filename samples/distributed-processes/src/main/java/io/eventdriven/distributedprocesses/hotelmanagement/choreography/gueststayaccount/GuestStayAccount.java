package io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount;

import java.util.UUID;

import static io.eventdriven.distributedprocesses.hotelmanagement.choreography.gueststayaccount.GuestStayAccountEvent.*;

public sealed interface GuestStayAccount {
  record Initial() implements GuestStayAccount {
  }

  record CheckedIn(
    UUID id,
    double balance
  ) implements GuestStayAccount {
  }

  record CheckedOut(
    UUID id,
    double balance
  ) implements GuestStayAccount {
  }

  static GuestStayAccount evolve(GuestStayAccount current, GuestStayAccountEvent event) {
    return switch (event) {
      case GuestCheckedIn opened: {
        if (!(current instanceof Initial))
          yield current;

        yield new CheckedIn(
          opened.guestStayAccountId(),
          0
        );
      }
      case ChargeRecorded chargeRecorded: {
        if (!(current instanceof CheckedIn checkedIn))
          yield current;

        yield new CheckedIn(
          checkedIn.id(),
          checkedIn.balance() - chargeRecorded.amount()
        );
      }
      case PaymentRecorded paymentRecorded: {
        if (!(current instanceof CheckedIn checkedIn))
          yield current;

        yield new CheckedIn(
          checkedIn.id(),
          checkedIn.balance() + paymentRecorded.amount()
        );
      }
      case GuestCheckedOut ignored: {
        if (!(current instanceof CheckedIn checkedIn))
          yield current;

        yield new CheckedOut(
          checkedIn.id(),
          checkedIn.balance()
        );
      }
      case GuestCheckoutFailed ignored: {
      }
      case null:
        throw new IllegalArgumentException("Event cannot be null!");
    };
  }

  static GuestStayAccount empty() {
    return new Initial();
  }
}
