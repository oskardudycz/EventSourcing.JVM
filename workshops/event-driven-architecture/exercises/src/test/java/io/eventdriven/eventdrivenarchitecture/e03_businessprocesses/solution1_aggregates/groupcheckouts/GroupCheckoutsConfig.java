package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.CommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    EventBus eventBus,
    CommandBus commandBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {
    eventBus.subscribe(GroupCheckoutEvent.GroupCheckoutInitiated.class, (event) -> {
      throw new RuntimeException("Handle GroupCheckout Initiated event");
    });
    // Add other eventBus subscriptions and commandBus handling when needed
    throw new RuntimeException("Configure group checkouts handlers here");
  }
}
