package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckout.CheckoutStatus.COMPLETED;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckout.CheckoutStatus.FAILED;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckoutEvent.*;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;

public class GroupCheckoutToDoList {
  private final MessageBus messageBus;

  public GroupCheckoutToDoList(MessageBus messageBus) {
    this.messageBus = messageBus;
  }

  public void handle(GroupCheckout groupCheckout) {
    var nextGuest = groupCheckout.nextGuestToCheckOut();

    if (nextGuest.isPresent()) {
      messageBus.publish(List.of(
        new CheckOutGuest(nextGuest.get(), groupCheckout.updatedAt(), groupCheckout.id())
      ));
      return;
    }

    complete(groupCheckout);
  }

  // This could be also checked by Cron
  private void complete(GroupCheckout groupCheckout) {
    messageBus.publish(List.of(
      !groupCheckout.areAnyFailedCheckouts()
        ? new GroupCheckoutCompleted(
        groupCheckout.id(),
        groupCheckout.checkoutsWith(COMPLETED),
        groupCheckout.updatedAt()
      )
        : new GroupCheckoutFailed(
        groupCheckout.id(),
        groupCheckout.checkoutsWith(COMPLETED),
        groupCheckout.checkoutsWith(FAILED),
        groupCheckout.updatedAt()
      )
    ));
  }
}
