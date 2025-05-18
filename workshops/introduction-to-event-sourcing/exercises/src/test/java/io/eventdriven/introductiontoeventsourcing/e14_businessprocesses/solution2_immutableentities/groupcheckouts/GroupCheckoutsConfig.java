package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.CommandBus;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventStore;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    EventStore eventStore,
    CommandBus commandBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {

    throw new RuntimeException("Configure group checkouts handler here");
  }
}
