package io.eventdriven.distributedprocesses.hotelmanagement.gueststayaccount;

import org.springframework.lang.Nullable;

import java.util.UUID;

public sealed interface GuestStayAccountCommand {
  record OpenGuestStayAccount(
    UUID guestStayAccountId
  ) implements GuestStayAccountCommand {
  }

  record RecordCharge(
    UUID guestStayAccountId,
    double amount
  ) implements GuestStayAccountCommand {
  }

  record RecordPayment(
    UUID guestStayAccountId,
    double amount
  ) implements GuestStayAccountCommand {
  }

  record CheckoutGuestAccount(
    UUID guestStayAccountId,
    @Nullable
    UUID groupCheckoutId
  ) implements GuestStayAccountCommand {
  }
}
