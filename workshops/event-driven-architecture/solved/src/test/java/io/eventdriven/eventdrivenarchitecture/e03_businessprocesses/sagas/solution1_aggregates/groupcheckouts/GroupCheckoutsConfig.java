package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    MessageBus messageBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {
    var saga = new GroupCheckoutSaga(messageBus);

    messageBus
      .subscribe(GroupCheckoutEvent.GroupCheckoutInitiated.class, saga::handle)
      .subscribe(GuestStayAccountEvent.GuestCheckedOut.class, saga::handle)
      .subscribe(GuestStayAccountEvent.GuestCheckoutFailed.class, saga::handle);

    messageBus
      .subscribe(RecordGuestCheckoutCompletion.class, groupCheckoutFacade::recordGuestCheckoutCompletion)
      .subscribe(RecordGuestCheckoutFailure.class, groupCheckoutFacade::recordGuestCheckoutFailure);
  }
}
