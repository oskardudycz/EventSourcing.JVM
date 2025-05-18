package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution1_aggregates.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.CommandBus;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventBus;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    EventBus eventBus,
    CommandBus commandBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {

    throw new RuntimeException("Configure group checkouts handler here");
  }
}
