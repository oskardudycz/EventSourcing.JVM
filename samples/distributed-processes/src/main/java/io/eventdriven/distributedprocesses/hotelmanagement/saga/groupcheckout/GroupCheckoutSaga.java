package io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout;

import static io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout.GroupCheckoutCommand.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.saga.groupcheckout.GroupCheckoutEvent.*;

import io.eventdriven.distributedprocesses.core.commands.CommandBus;
import io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountCommand;
import io.eventdriven.distributedprocesses.hotelmanagement.saga.gueststayaccount.GuestStayAccountEvent;

public class GroupCheckoutSaga {
  private final CommandBus commandBus;

  public GroupCheckoutSaga(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  public void on(GroupCheckoutInitiated groupCheckoutInitiated) {
    for (var guestAccountId : groupCheckoutInitiated.guestStayAccountIds()) {
      commandBus.schedule(new GuestStayAccountCommand.CheckOutGuest(
          guestAccountId,
          groupCheckoutInitiated.groupCheckoutId(),
          groupCheckoutInitiated.initiatedAt()));
    }
    commandBus.schedule(new RecordGuestCheckoutsInitiation(
        groupCheckoutInitiated.groupCheckoutId(),
        groupCheckoutInitiated.guestStayAccountIds(),
        groupCheckoutInitiated.initiatedAt()));
  }

  public void on(GuestStayAccountEvent.GuestCheckedOut guestCheckoutCompleted) {
    if (guestCheckoutCompleted.groupCheckoutId() == null) return;

    commandBus.schedule(new RecordGuestCheckoutCompletion(
        guestCheckoutCompleted.groupCheckoutId(),
        guestCheckoutCompleted.guestStayAccountId(),
        guestCheckoutCompleted.completedAt()));
  }

  public void on(GuestStayAccountEvent.GuestCheckoutFailed guestCheckoutFailed) {
    if (guestCheckoutFailed.groupCheckoutId() == null) return;

    commandBus.schedule(new RecordGuestCheckoutFailure(
        guestCheckoutFailed.groupCheckoutId(),
        guestCheckoutFailed.guestStayAccountId(),
        guestCheckoutFailed.failedAt()));
  }
}
