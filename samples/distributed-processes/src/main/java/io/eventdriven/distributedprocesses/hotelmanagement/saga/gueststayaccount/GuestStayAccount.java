package io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount;

import java.util.UUID;

import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountEvent.*;

public interface GuestStayAccount {
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

//  public static GuestStayAccount checkIn(UUID guestStayAccountId, OffsetDateTime openedAt) {
//    return new GuestStayAccount(
//      guestStayAccountId,
//      openedAt
//    );
//  }
//
//  private GuestStayAccount(
//    UUID guestStayAccountId,
//    OffsetDateTime openedAt
//  ) {
//    enqueue(new GuestCheckedIn(guestStayAccountId, openedAt));
//  }
//
//  public void recordCharge(double amount, OffsetDateTime now) {
//    if (status != Status.Open)
//      throw new RuntimeException("Cannot record charge if status is other than Open");
//
//    enqueue(new ChargeRecorded(id(), amount, now));
//  }
//
//  public void recordPayment(double amount, OffsetDateTime now) {
//    if (status != Status.Open)
//      throw new RuntimeException("Cannot record payment if status is other than Open");
//
//    enqueue(new PaymentRecorded(id(), amount, now));
//  }
//
//  public void checkout(@Nullable UUID groupCheckoutId, OffsetDateTime now) {
//    if (status != Status.Open || balance != 0) {
//      enqueue(new GuestCheckoutFailed(id(), groupCheckoutId, OffsetDateTime.now()));
//      return;
//    }
//    enqueue(new GuestCheckedOut(id(), groupCheckoutId, now));
//  }

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
