package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.*;

public final class GuestStayAccountsConfig {
  public static void configureGuestStayAccounts(
    MessageBus messageBus,
    GuestStayAccountFacade guestStayFacade
  )
  {
    messageBus.subscribe(CheckOutGuest.class, guestStayFacade::checkOutGuest);
  }
}
