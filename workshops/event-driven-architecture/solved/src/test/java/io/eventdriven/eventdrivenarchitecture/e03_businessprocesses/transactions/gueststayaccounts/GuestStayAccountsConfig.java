package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    MessageBus messageBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
  }
}
