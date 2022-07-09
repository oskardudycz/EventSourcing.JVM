package io.eventdriven.distributedprocesses.hotelmanagement.groupcheckout;

import io.eventdriven.distributedprocesses.core.commands.CommandBus;
import io.eventdriven.distributedprocesses.hotelmanagement.gueststayaccount.GuestStayAccountEvent;

import static io.eventdriven.distributedprocesses.hotelmanagement.groupcheckout.GroupCheckoutCommand.*;
import static io.eventdriven.distributedprocesses.hotelmanagement.groupcheckout.GroupCheckoutEvent.GroupCheckoutInitiated;
import static io.eventdriven.distributedprocesses.hotelmanagement.gueststayaccount.GuestStayAccountCommand.CheckoutGuestAccount;

public class GroupCheckoutSaga {
  private final CommandBus commandBus;

  public GroupCheckoutSaga(CommandBus commandBus) {
    this.commandBus = commandBus;
  }

  public void on(GroupCheckoutInitiated groupCheckoutInitiated) {
    for (var guestAccountId : groupCheckoutInitiated.guestStayAccountIds()) {
      commandBus.send(
        new CheckoutGuestAccount(guestAccountId, groupCheckoutInitiated.groupCheckoutId())
      );
    }
    commandBus.send(
      new RecordGuestStayInitiation(groupCheckoutInitiated.groupCheckoutId(), groupCheckoutInitiated.guestStayAccountIds())
    );
  }

  public void on(GuestStayAccountEvent.GuestAccountCheckoutCompleted guestCheckoutCompleted) {
    if (guestCheckoutCompleted.groupCheckoutId() == null)
      return;

    commandBus.send(
      new RecordGuestCheckoutCompletion(
        guestCheckoutCompleted.groupCheckoutId(),
        guestCheckoutCompleted.guestStayAccountId(),
        guestCheckoutCompleted.completedAt()
      )
    );
  }

  public void on(GuestStayAccountEvent.GuestAccountCheckoutFailed guestCheckoutFailed) {
    if (guestCheckoutFailed.groupCheckoutId() == null)
      return;

    commandBus.send(
      new RecordGuestCheckoutFailure(
        guestCheckoutFailed.groupCheckoutId(),
        guestCheckoutFailed.guestStayAccountId(),
        guestCheckoutFailed.failedAt()
      )
    );
  }
}
