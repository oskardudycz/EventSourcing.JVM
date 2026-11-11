package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

import java.util.List;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckout.CheckoutStatus.COMPLETED;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckout.CheckoutStatus.FAILED;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckoutEvent.*;

public class GroupCheckoutToDoList {
  private final GroupCheckoutProjection projection;
  private final MessageBus messageBus;

  public GroupCheckoutToDoList(GroupCheckoutProjection projection, MessageBus messageBus) {
    this.projection = projection;
    this.messageBus = messageBus;
  }

  public List<Class<?>> canHandle() {
    return projection.canHandle();
  }

  public void handle(Object event) {
    projection.handle(event).ifPresent(this::tryComplete);
  }

  public void tryComplete(GroupCheckout groupCheckout) {
    if (groupCheckout.areAnyOngoingCheckouts())
      return;

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
