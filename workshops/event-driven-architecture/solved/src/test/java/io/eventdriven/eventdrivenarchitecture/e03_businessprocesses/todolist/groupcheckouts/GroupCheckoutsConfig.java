package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountEvent;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    MessageBus messageBus,
    GroupCheckoutToDoList groupCheckoutToDoList
  ) {
    messageBus
      .subscribe(GroupCheckoutEvent.GroupCheckoutInitiated.class, groupCheckoutToDoList::groupCheckoutInitiated)
      .subscribe(GuestStayAccountEvent.GuestCheckedOut.class, groupCheckoutToDoList::guestCheckedOut)
      .subscribe(GuestStayAccountEvent.GuestCheckoutFailed.class, groupCheckoutToDoList::guestCheckoutFailed);
  }
}
