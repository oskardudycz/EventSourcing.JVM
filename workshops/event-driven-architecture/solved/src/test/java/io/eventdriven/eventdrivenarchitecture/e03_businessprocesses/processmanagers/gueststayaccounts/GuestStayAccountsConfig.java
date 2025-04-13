package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.CommandBus;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    CommandBus commandBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
    commandBus.handle(GuestStayAccountFacade.GuestStayAccountCommand.CheckOutGuest.class, guestStayFacade::checkOutGuest);
  }
}
