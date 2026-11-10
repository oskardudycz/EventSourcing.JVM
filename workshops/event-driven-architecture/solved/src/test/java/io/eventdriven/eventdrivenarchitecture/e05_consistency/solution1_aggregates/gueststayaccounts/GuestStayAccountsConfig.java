package io.eventdriven.eventdrivenarchitecture.e05_consistency.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e05_consistency.core.MessageBus;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    MessageBus messageBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
    messageBus.subscribe(GuestStayAccountFacade.GuestStayAccountCommand.CheckOutGuest.class, guestStayFacade::checkOutGuest);
  }
}
