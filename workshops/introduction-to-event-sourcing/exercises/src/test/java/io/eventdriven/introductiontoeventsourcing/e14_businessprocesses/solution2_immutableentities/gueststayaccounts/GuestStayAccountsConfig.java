package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.CommandBus;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    CommandBus commandBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
    commandBus.handle(CheckOutGuest.class, guestStayFacade::checkOutGuest);
  }
}
