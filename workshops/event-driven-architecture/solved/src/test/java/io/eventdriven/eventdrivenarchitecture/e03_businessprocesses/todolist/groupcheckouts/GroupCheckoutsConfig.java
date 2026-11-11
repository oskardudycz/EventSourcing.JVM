package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    MessageBus messageBus,
    GroupCheckoutToDoList groupCheckoutToDoList
  ) {
    for (var eventType : groupCheckoutToDoList.canHandle()) {
      messageBus.subscribe(eventType, groupCheckoutToDoList::handle);
    }
  }
}
