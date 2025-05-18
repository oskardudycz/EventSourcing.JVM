package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution1_aggregates.groupcheckouts;


import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.core.SagaResult;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution1_aggregates.gueststayaccounts.GuestStayAccountEvent;

import java.util.Arrays;

import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.core.SagaResult.*;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution1_aggregates.gueststayaccounts.GuestStayAccountFacade.GuestStayAccountCommand.*;
import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

public final class GroupCheckoutSaga
{
  public static Command<CheckOutGuest>[] handle(GroupCheckoutEvent.GroupCheckoutInitiated event) {
    return Arrays.stream(event.guestStayAccountIds())
      .map(guestAccountId ->
        Send(
          new CheckOutGuest(guestAccountId, event.initiatedAt(), event.groupCheckoutId())
        )
      ).toArray(Command[]::new);
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

