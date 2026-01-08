package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.ICommandBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.IEventBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.SagaResult;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent;

import java.util.Arrays;
import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    IEventBus eventBus,
    ICommandBus commandBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {
    eventBus
      .subscribe(GroupCheckoutEvent.GroupCheckoutInitiated.class, (event) ->
        commandBus.send(GroupCheckoutSaga.handle(event).stream().map(SagaResult.Command::message).toList())
      )
      .subscribe(GuestStayAccountEvent.GuestCheckedOut.class, (event) -> {
        var result = GroupCheckoutSaga.handle(event);

        if(result instanceof SagaResult.Command(RecordGuestCheckoutCompletion message)) {
          commandBus.send(List.of(message));
        }
      })
      .subscribe(GuestStayAccountEvent.GuestCheckoutFailed.class, (event) -> {
        var result = GroupCheckoutSaga.handle(event);

        if(result instanceof SagaResult.Command(RecordGuestCheckoutFailure message)) {
          commandBus.send(List.of(message));
        }
      });

    commandBus
      .handle(RecordGuestCheckoutCompletion.class, groupCheckoutFacade::recordGuestCheckoutCompletion)
      .handle(RecordGuestCheckoutFailure.class, groupCheckoutFacade::recordGuestCheckoutFailure);
  }
}
