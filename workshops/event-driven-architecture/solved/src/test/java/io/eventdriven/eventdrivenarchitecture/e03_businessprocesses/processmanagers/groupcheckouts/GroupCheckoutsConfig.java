package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.gueststayaccounts.GuestStayAccountEvent;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    EventBus eventBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {
    eventBus
      .subscribe(GuestStayAccountEvent.GuestCheckedOut.class, groupCheckoutFacade::recordGuestCheckoutCompletion)
      .subscribe(GuestStayAccountEvent.GuestCheckoutFailed.class, groupCheckoutFacade::recordGuestCheckoutFailure);
  }
}
