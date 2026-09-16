package io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount;

import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.testing.EventSourcedSpecification;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountDecider.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountEvent.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.*;

public class GuestStayAccountCheckinTests extends EventSourcedSpecification<GuestStayAccount, GuestStayAccountEvent> {
  private final OffsetDateTime now = OffsetDateTime.now();
  private final UUID guestStayAccountId = UUID.randomUUID();
  private final UUID groupCheckoutId = UUID.randomUUID();
  private final double amount = 123.45;

  private final CheckInGuest checkIn = new CheckInGuest(guestStayAccountId, ETag.weak(1), now);
  private final GuestCheckedIn checkedIn = new GuestCheckedIn(guestStayAccountId, now);

  protected GuestStayAccountCheckinTests() {
    super(GuestStayAccount::empty, GuestStayAccount::evolve);
  }

  @Test
  public void givenNonExistingGuestStayAccount_WhenCheckIn_ThenSucceeds() {
    given()
      .when(current -> handle(checkIn, current))
      .then(
        new GuestCheckedIn(guestStayAccountId, now)
      );
  }

  @Test
  public void givenNonExistingGuestStayAccount_WhenCheckIn_ThenStampedWithTheCallersClock() {
    var tomorrow = now.plusDays(1);

    given()
      .when(current -> handle(new CheckInGuest(guestStayAccountId, ETag.weak(1), tomorrow), current))
      .then(
        new GuestCheckedIn(guestStayAccountId, tomorrow)
      );
  }

  @Test
  public void givenCheckedInGuestStayAccount_WhenCheckInAgain_ThenThrows() {
    given(() -> new GuestStayAccountEvent[]{checkedIn})
      .when(current -> handle(checkIn, current))
      .thenThrows(IllegalStateException.class);
  }

  @Test
  public void givenGuestStayAccountWithCharges_WhenCheckIn_ThenThrows() {
    given(() -> new GuestStayAccountEvent[]{
      checkedIn,
      new ChargeRecorded(guestStayAccountId, amount, now)
    })
      .when(current -> handle(checkIn, current))
      .thenThrows(IllegalStateException.class);
  }

  @Test
  public void givenCheckedOutGuestStayAccount_WhenCheckIn_ThenThrows() {
    given(() -> new GuestStayAccountEvent[]{
      checkedIn,
      new GuestCheckedOut(guestStayAccountId, groupCheckoutId, now)
    })
      .when(current -> handle(checkIn, current))
      .thenThrows(IllegalStateException.class);
  }
}
