package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.choreography.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.choreography.gueststayaccounts.GuestStayAccountEvent;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.CommandBus;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventStore;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    EventStore eventStore,
    CommandBus commandBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {
    eventStore
      .subscribe(GuestStayAccountEvent.GuestCheckedOut.class, groupCheckoutFacade::recordGuestCheckoutCompletion)
      .subscribe(GuestStayAccountEvent.GuestCheckoutFailed.class, groupCheckoutFacade::recordGuestCheckoutFailure);
  }
}
