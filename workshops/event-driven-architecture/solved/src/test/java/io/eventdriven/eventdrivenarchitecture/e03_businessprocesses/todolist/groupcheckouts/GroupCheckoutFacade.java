package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckoutEvent.GroupCheckoutInitiated;

public class GroupCheckoutFacade {
  private final MessageBus messageBus;

  public GroupCheckoutFacade(MessageBus messageBus) {
    this.messageBus = messageBus;
  }

  public void initiateGroupCheckout(GroupCheckoutCommand.InitiateGroupCheckout command) {
    messageBus.publish(List.of(
      new GroupCheckoutInitiated(command.groupCheckoutId(), command.clerkId(), command.guestStayIds(), command.now())
    ));
  }

  public sealed interface GroupCheckoutCommand {
    record InitiateGroupCheckout(
      UUID groupCheckoutId,
      UUID clerkId,
      UUID[] guestStayIds,
      OffsetDateTime now
    ) implements GroupCheckoutCommand {
    }
  }
}
