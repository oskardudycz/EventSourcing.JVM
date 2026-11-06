package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    MessageBus messageBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
    messageBus.subscribe(CheckOutGuest.class, guestStayFacade::checkOutGuest);
  }
}
