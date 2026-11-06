package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    MessageBus messageBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
    messageBus.subscribe(GuestStayAccountFacade.GuestStayAccountCommand.CheckOutGuest.class, guestStayFacade::checkOutGuest);
  }
}
