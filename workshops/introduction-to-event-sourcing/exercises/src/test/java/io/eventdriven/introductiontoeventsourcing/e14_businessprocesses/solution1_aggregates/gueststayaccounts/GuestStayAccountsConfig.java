package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution1_aggregates.gueststayaccounts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.CommandBus;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    CommandBus commandBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
    commandBus.handle(GuestStayAccountFacade.GuestStayAccountCommand.CheckOutGuest.class, guestStayFacade::checkOutGuest);
  }
}
