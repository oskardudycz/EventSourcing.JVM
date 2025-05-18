package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventStore;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.gueststayaccounts.GuestStayAccountEvent;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    EventStore eventStore,
    GroupCheckoutFacade groupCheckoutFacade
  ) {
    eventStore
      .subscribe(GuestStayAccountEvent.GuestCheckedOut.class, groupCheckoutFacade::recordGuestCheckoutCompletion)
      .subscribe(GuestStayAccountEvent.GuestCheckoutFailed.class, groupCheckoutFacade::recordGuestCheckoutFailure);
  }
}
