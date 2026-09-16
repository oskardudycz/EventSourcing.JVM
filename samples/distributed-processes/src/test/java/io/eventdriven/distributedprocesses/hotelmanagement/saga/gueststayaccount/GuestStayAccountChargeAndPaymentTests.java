package io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount;

import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.testing.EventSourcedSpecification;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountDecider.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountEvent.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.*;

public class GuestStayAccountChargeAndPaymentTests extends EventSourcedSpecification<GuestStayAccount, GuestStayAccountEvent> {
  private final OffsetDateTime now = OffsetDateTime.now();
  private final UUID guestStayAccountId = UUID.randomUUID();
  private final UUID groupCheckoutId = UUID.randomUUID();
  private final double amount = 123.45;

  private final GuestCheckedIn checkedIn = new GuestCheckedIn(guestStayAccountId, now);
  private final RecordCharge recordCharge =
    new RecordCharge(guestStayAccountId, amount, ETag.weak(1), now);
  private final RecordPayment recordPayment =
    new RecordPayment(guestStayAccountId, amount, ETag.weak(1), now);

  protected GuestStayAccountChargeAndPaymentTests() {
    super(GuestStayAccount::empty, GuestStayAccount::evolve);
  }

  @Test
  public void givenCheckedInGuestStayAccount_WhenRecordCharge_ThenChargeRecorded() {
    given(() -> new GuestStayAccountEvent[]{checkedIn})
      .when(current -> handle(recordCharge, current))
      .then(
        new ChargeRecorded(guestStayAccountId, amount, now)
      );
  }

  @Test
  public void givenCheckedInGuestStayAccount_WhenRecordPayment_ThenPaymentRecorded() {
    given(() -> new GuestStayAccountEvent[]{checkedIn})
      .when(current -> handle(recordPayment, current))
      .then(
        new PaymentRecorded(guestStayAccountId, amount, now)
      );
  }

  @Test
  public void givenGuestStayAccountWithACharge_WhenRecordPayment_ThenPaymentRecorded() {
    given(() -> new GuestStayAccountEvent[]{
      checkedIn,
      new ChargeRecorded(guestStayAccountId, amount, now)
    })
      .when(current -> handle(recordPayment, current))
      .then(
        new PaymentRecorded(guestStayAccountId, amount, now)
      );
  }

  @Test
  public void givenNonExistingGuestStayAccount_WhenRecordCharge_ThenThrows() {
    given()
      .when(current -> handle(recordCharge, current))
      .thenThrows(IllegalStateException.class);
  }

  @Test
  public void givenNonExistingGuestStayAccount_WhenRecordPayment_ThenThrows() {
    given()
      .when(current -> handle(recordPayment, current))
      .thenThrows(IllegalStateException.class);
  }

  @Test
  public void givenCheckedOutGuestStayAccount_WhenRecordCharge_ThenThrows() {
    given(() -> new GuestStayAccountEvent[]{
      checkedIn,
      new GuestCheckedOut(guestStayAccountId, groupCheckoutId, now)
    })
      .when(current -> handle(recordCharge, current))
      .thenThrows(IllegalStateException.class);
  }

  @Test
  public void givenCheckedOutGuestStayAccount_WhenRecordPayment_ThenThrows() {
    given(() -> new GuestStayAccountEvent[]{
      checkedIn,
      new GuestCheckedOut(guestStayAccountId, groupCheckoutId, now)
    })
      .when(current -> handle(recordPayment, current))
      .thenThrows(IllegalStateException.class);
  }
}
