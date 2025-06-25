package io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution1_aggregates.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core.CommandBus;
import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core.EventStore;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    EventStore eventStore,
    CommandBus commandBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {

    throw new RuntimeException("Configure group checkouts handler here");
  }
}
