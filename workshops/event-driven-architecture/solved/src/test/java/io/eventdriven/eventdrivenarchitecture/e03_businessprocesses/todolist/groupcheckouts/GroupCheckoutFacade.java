package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.groupcheckouts.GroupCheckoutEvent.GroupCheckoutInitiated;
import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.todolist.gueststayaccounts.GuestStayAccountDecider.GuestStayAccountCommand.CheckOutGuest;

public class GroupCheckoutFacade {
  private final MessageBus messageBus;

  public GroupCheckoutFacade(MessageBus messageBus) {
    this.messageBus = messageBus;
  }

  public void initiateGroupCheckout(GroupCheckoutCommand.InitiateGroupCheckout command) {
    messageBus.publish(
      Stream.concat(
        Stream.of(new GroupCheckoutInitiated(command.groupCheckoutId(), command.clerkId(), command.guestStayIds(), command.now())),
        Arrays.stream(command.guestStayIds())
          .map(guestStayId -> new CheckOutGuest(guestStayId, command.now(), command.groupCheckoutId()))
      ).toList()
    );
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
