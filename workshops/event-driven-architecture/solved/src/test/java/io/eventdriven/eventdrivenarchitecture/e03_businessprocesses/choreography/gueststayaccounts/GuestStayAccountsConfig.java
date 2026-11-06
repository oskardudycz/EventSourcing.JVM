package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.choreography.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    MessageBus messageBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
    messageBus.subscribe(CheckOutGuest.class, guestStayFacade::checkOutGuest);
  }
}
