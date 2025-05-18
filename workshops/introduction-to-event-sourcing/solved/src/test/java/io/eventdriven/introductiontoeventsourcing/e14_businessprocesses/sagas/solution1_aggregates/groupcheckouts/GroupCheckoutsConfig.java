package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution1_aggregates.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.CommandBus;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventStore;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.core.SagaResult;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import java.util.Arrays;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    EventStore eventStore,
    CommandBus commandBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {
    eventStore
      .subscribe(GroupCheckoutEvent.GroupCheckoutInitiated.class, (event) ->
        commandBus.send(Arrays.stream(GroupCheckoutSaga.handle(event)).map(SagaResult.Command::message).toArray())
      )
      .subscribe(GuestStayAccountEvent.GuestCheckedOut.class, (event) -> {
        var result = GroupCheckoutSaga.handle(event);

        if(result instanceof SagaResult.Command(RecordGuestCheckoutCompletion message)) {
          commandBus.send(new Object[]{message});
        }
      })
      .subscribe(GuestStayAccountEvent.GuestCheckoutFailed.class, (event) -> {
        var result = GroupCheckoutSaga.handle(event);

        if(result instanceof SagaResult.Command(RecordGuestCheckoutFailure message)) {
          commandBus.send(new Object[]{message});
        }
      });

    commandBus
      .handle(RecordGuestCheckoutCompletion.class, groupCheckoutFacade::recordGuestCheckoutCompletion)
      .handle(RecordGuestCheckoutFailure.class, groupCheckoutFacade::recordGuestCheckoutFailure);
  }
}
