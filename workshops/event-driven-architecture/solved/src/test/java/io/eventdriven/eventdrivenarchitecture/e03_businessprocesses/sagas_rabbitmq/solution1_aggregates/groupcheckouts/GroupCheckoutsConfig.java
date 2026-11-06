package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.core.SagaResult;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;

import java.util.Arrays;
import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

public final class GroupCheckoutsConfig {
  public static void configureGroupCheckouts(
    MessageBus messageBus,
    GroupCheckoutFacade groupCheckoutFacade
  ) {
    messageBus
      .subscribe(GroupCheckoutEvent.GroupCheckoutInitiated.class, (event) ->
        messageBus.publish(GroupCheckoutSaga.handle(event).stream().map(SagaResult.Command::message).toList())
      )
      .subscribe(GuestStayAccountEvent.GuestCheckedOut.class, (event) -> {
        var result = GroupCheckoutSaga.handle(event);

        if(result instanceof SagaResult.Command(RecordGuestCheckoutCompletion message)) {
          messageBus.publish(List.of(message));
        }
      })
      .subscribe(GuestStayAccountEvent.GuestCheckoutFailed.class, (event) -> {
        var result = GroupCheckoutSaga.handle(event);

        if (result instanceof SagaResult.Command(
          RecordGuestCheckoutFailure message
        )) {
          messageBus.publish(List.of(message));
        }
      });

    messageBus
      .subscribe(RecordGuestCheckoutCompletion.class, groupCheckoutFacade::recordGuestCheckoutCompletion)
      .subscribe(RecordGuestCheckoutFailure.class, groupCheckoutFacade::recordGuestCheckoutFailure);
  }
}
