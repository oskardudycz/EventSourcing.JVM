package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts;


import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.SagaResult;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccountEvent;

import java.util.Arrays;
import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.core.SagaResult.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.groupcheckouts.GroupCheckoutDecider.GroupCheckoutCommand.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_kafka.solution2_immutableentities.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;

public final class GroupCheckoutSaga
{
  public static List<Command<CheckOutGuest>> handle(GroupCheckoutEvent.GroupCheckoutInitiated event) {
    return Arrays.stream(event.guestStayAccountIds())
      .map(guestAccountId ->
        Send(
          new CheckOutGuest(guestAccountId, event.initiatedAt(), event.groupCheckoutId())
        )
      ).toList();
  }

  public static SagaResult handle(GuestStayAccountEvent.GuestCheckedOut event) {
    if (event.groupCheckoutId() == null)
      return Ignore;

    return Send(
      new RecordGuestCheckoutCompletion(
        event.groupCheckoutId(),
        event.guestStayAccountId(),
        event.completedAt()
      )
    );
  }

  public static SagaResult handle(GuestStayAccountEvent.GuestCheckoutFailed event) {
    if (event.groupCheckoutId() == null)
      return Ignore;

    return Send(
      new RecordGuestCheckoutFailure(
        event.groupCheckoutId(),
        event.guestStayAccountId(),
        event.failedAt()
      )
    );
  }
}

