package io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount;

import io.eventdriven.testing.EventSourcedSpecification;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountDecider.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountEvent.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.*;

public class GuestStayAccountCheckoutTests extends EventSourcedSpecification<GuestStayAccount, GuestStayAccountEvent> {
  private final OffsetDateTime now = OffsetDateTime.now();
  private final UUID guestStayAccountId = UUID.randomUUID();
  private final UUID groupCheckoutId = UUID.randomUUID();
  private final double amount = 123.45;

  private final GuestCheckedIn checkedIn = new GuestCheckedIn(guestStayAccountId, now);
  private final CheckOutGuest checkOut =
    new CheckOutGuest(guestStayAccountId, groupCheckoutId, now);

  private final GuestCheckedOut checkedOut =
    new GuestCheckedOut(guestStayAccountId, groupCheckoutId, now);
  private final GuestCheckoutFailed checkoutFailed = new GuestCheckoutFailed(
    guestStayAccountId,
    GuestCheckoutFailed.Reason.BalanceNotSettled,
    groupCheckoutId,
    now
  );
  private final GuestCheckoutFailed checkoutFailedAsInvalid = new GuestCheckoutFailed(
    guestStayAccountId,
    GuestCheckoutFailed.Reason.InvalidState,
    groupCheckoutId,
    now
  );

  protected GuestStayAccountCheckoutTests() {
    super(GuestStayAccount::empty, GuestStayAccount::evolve);
  }

  @Test
  public void givenSettledGuestStayAccount_WhenCheckOut_ThenGuestCheckedOut() {
    given(() -> new GuestStayAccountEvent[]{checkedIn})
      .when(current -> handle(checkOut, current))
      .then(
        checkedOut
      );
  }

  @Test
  public void givenSettledGuestStayAccount_WhenCheckOutOutsideAGroup_ThenNoGroupIsNamed() {
    given(() -> new GuestStayAccountEvent[]{checkedIn})
      .when(current -> handle(new CheckOutGuest(guestStayAccountId, null, now), current))
      .then(
        new GuestCheckedOut(guestStayAccountId, null, now)
      );
  }

  @Test
  public void givenGuestStayAccountWithChargeAndMatchingPayment_WhenCheckOut_ThenGuestCheckedOut() {
    given(() -> new GuestStayAccountEvent[]{
      checkedIn,
      new ChargeRecorded(guestStayAccountId, amount, now),
      new PaymentRecorded(guestStayAccountId, amount, now)
    })
      .when(current -> handle(checkOut, current))
      .then(
        checkedOut
      );
  }

  @Test
  public void givenGuestStayAccountWithManyChargesAndPaymentsThatBalance_WhenCheckOut_ThenGuestCheckedOut() {
    given(() -> new GuestStayAccountEvent[]{
      checkedIn,
      new ChargeRecorded(guestStayAccountId, 100, now),
      new PaymentRecorded(guestStayAccountId, 30, now),
      new ChargeRecorded(guestStayAccountId, 20, now),
      new PaymentRecorded(guestStayAccountId, 90, now)
    })
      .when(current -> handle(checkOut, current))
      .then(
        checkedOut
      );
  }

  @Test
  public void givenGuestStayAccountWithUnpaidCharge_WhenCheckOut_ThenCheckoutFailsAsNotSettled() {
    given(() -> new GuestStayAccountEvent[]{
      checkedIn,
      new ChargeRecorded(guestStayAccountId, amount, now)
    })
      .when(current -> handle(checkOut, current))
      .then(
        checkoutFailed
      );
  }

  @Test
  public void givenOverpaidGuestStayAccount_WhenCheckOut_ThenCheckoutFailsAsNotSettled() {
    given(() -> new GuestStayAccountEvent[]{
      checkedIn,
      new PaymentRecorded(guestStayAccountId, amount, now)
    })
      .when(current -> handle(checkOut, current))
      .then(
        checkoutFailed
      );
  }

  // A checkout never throws, because the group checkout waits on its answer.
  @Test
  public void givenNonExistingGuestStayAccount_WhenCheckOut_ThenCheckoutFailsAsInvalidState() {
    given()
      .when(current -> handle(checkOut, current))
      .then(
        checkoutFailedAsInvalid
      );
  }

  @Test
  public void givenCheckedOutGuestStayAccount_WhenCheckOutAgain_ThenCheckoutFailsAsInvalidState() {
    given(() -> new GuestStayAccountEvent[]{checkedIn, checkedOut})
      .when(current -> handle(checkOut, current))
      .then(
        checkoutFailedAsInvalid
      );
  }

  // Replaying the failure must leave the account exactly as it was, so the guest can settle and
  // try again. If evolve chokes on it, this Given never gets to the When.
  @Test
  public void givenGuestStayAccountWithFailedCheckout_WhenSettledAndCheckedOut_ThenGuestCheckedOut() {
    given(() -> new GuestStayAccountEvent[]{
      checkedIn,
      new ChargeRecorded(guestStayAccountId, amount, now),
      checkoutFailed,
      new PaymentRecorded(guestStayAccountId, amount, now)
    })
      .when(current -> handle(checkOut, current))
      .then(
        checkedOut
      );
  }

  @Test
  public void givenGuestStayAccountWithFailedCheckout_WhenStillUnsettled_ThenCheckoutFailsAgain() {
    given(() -> new GuestStayAccountEvent[]{
      checkedIn,
      new ChargeRecorded(guestStayAccountId, amount, now),
      checkoutFailed
    })
      .when(current -> handle(checkOut, current))
      .then(
        checkoutFailed
      );
  }
}
