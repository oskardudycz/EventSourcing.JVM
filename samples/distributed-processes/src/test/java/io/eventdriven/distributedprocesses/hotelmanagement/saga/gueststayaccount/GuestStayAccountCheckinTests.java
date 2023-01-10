package io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount;

import io.eventdriven.distributedprocesses.core.http.ETag;
import io.eventdriven.testing.EventSourcedSpecification;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.util.Arrays.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountDecider.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountEvent.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.*;

public class GuestStayAccountCheckinTests extends EventSourcedSpecification<GuestStayAccount, GuestStayAccountEvent> {
  private final OffsetDateTime now = OffsetDateTime.now();
  private final UUID guestStayAccountId = UUID.randomUUID();

  protected GuestStayAccountCheckinTests() {
    super(GuestStayAccount::empty, GuestStayAccount::evolve);
  }

  @Test
  public void givenNonExistingGuestStayAccount_WhenCheckIn_ThenSucceeds() {
    given()
      .when(current -> array(
        handle(new CheckInGuest(guestStayAccountId, ETag.weak(1), now), current)
      ))
      .then(
        new GuestCheckedIn(guestStayAccountId, now)
      );
  }
}
