package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.CommandBus;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    CommandBus commandBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
    commandBus.handle(CheckOutGuest.class, guestStayFacade::checkOutGuest);
  }
}
