package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.CommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountEvent;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    EventBus eventBus,
    CommandBus commandBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {
    eventBus
      .subscribe(GuestStayAccountEvent.GuestCheckedOut.class, groupCheckoutFacade::recordGuestCheckoutCompletion)
      .subscribe(GuestStayAccountEvent.GuestCheckoutFailed.class, groupCheckoutFacade::recordGuestCheckoutFailure);
  }
}
