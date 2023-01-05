package io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount;

import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccount.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountEvent.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand.*;

public final class GuestStayAccountDecider {
  public static GuestCheckedIn handle(CheckInGuest command, GuestStayAccount current){
    if (!(current instanceof Initial))
      throw new IllegalStateException("Guest already checked in");

    return new GuestCheckedIn(
      command.guestStayAccountId(),
      command.now()
    );
  }
}
