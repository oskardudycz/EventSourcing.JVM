package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution1_aggregates.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ICommandBus;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    ICommandBus commandBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
    commandBus.handle(GuestStayAccountFacade.GuestStayAccountCommand.CheckOutGuest.class, guestStayFacade::checkOutGuest);
  }
}
