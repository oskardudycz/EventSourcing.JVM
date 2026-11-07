package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    MessageBus messageBus,
    GroupCheckoutProjection groupCheckoutProjection,
    GroupCheckoutToDoList groupCheckoutToDoList
  ) {
    for (var eventType : groupCheckoutProjection.canHandle()) {
      messageBus.subscribe(eventType, event ->
        groupCheckoutProjection.handle(event).ifPresent(groupCheckoutToDoList::handle)
      );
    }
  }
}
