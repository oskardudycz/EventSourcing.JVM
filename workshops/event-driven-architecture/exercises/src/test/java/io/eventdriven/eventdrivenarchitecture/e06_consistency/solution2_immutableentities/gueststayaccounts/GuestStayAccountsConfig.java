package io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e06_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e06_consistency.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    MessageBus messageBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
    messageBus.subscribe(CheckOutGuest.class, guestStayFacade::checkOutGuest);
  }
}
