package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.CommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.core.SagaResult;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import java.util.Arrays;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    EventBus eventBus,
    CommandBus commandBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {

    throw new RuntimeException("Configure group checkouts handler here");
  }
}
