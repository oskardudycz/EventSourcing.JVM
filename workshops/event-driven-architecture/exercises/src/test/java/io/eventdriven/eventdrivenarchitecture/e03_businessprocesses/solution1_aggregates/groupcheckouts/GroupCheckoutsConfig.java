package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    MessageBus messageBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {
    messageBus.subscribe(GroupCheckoutEvent.GroupCheckoutInitiated.class, (event) -> {
      throw new RuntimeException("Handle GroupCheckout Initiated event");
    });
    // Add other message bus subscriptions for commands and events when needed
    throw new RuntimeException("Configure group checkouts handlers here");
  }
}
