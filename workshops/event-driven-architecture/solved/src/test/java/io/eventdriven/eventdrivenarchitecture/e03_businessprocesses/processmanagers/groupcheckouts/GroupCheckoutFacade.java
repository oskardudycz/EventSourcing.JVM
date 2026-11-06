package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.core.Message;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.gueststayaccounts.GuestStayAccountEvent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.processmanagers.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.InitiateGroupCheckout;

public class GroupCheckoutFacade {
  private final Database.Collection<GroupCheckout> collection;
  private final MessageBus messageBus;

  public GroupCheckoutFacade(Database.Collection<GroupCheckout> collection, MessageBus messageBus) {
    this.collection = collection;
    this.messageBus = messageBus;
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    var groupCheckout = GroupCheckout.handle(command);

    collection.store(command.groupCheckoutId(), groupCheckout);
    publish(groupCheckout.dequeueUncommittedMessages());
  }

  public void recordGuestCheckoutCompletion(GuestStayAccountEvent.GuestCheckedOut event) {
    if (event.groupCheckoutId() == null)
      return;

    var groupCheckout = collection.get(event.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.on(event);

    collection.store(event.groupCheckoutId(), groupCheckout);
    publish(groupCheckout.dequeueUncommittedMessages());
  }

  public void recordGuestCheckoutFailure(GuestStayAccountEvent.GuestCheckoutFailed event) {
    var groupCheckout = collection.get(event.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.on(event);

    collection.store(event.groupCheckoutId(), groupCheckout);
    publish(groupCheckout.dequeueUncommittedMessages());
  }

  void publish(Message[] messages) {
    for (Message message : messages) {
      switch (message) {
        case Message.Command command -> messageBus.publish(List.of(command.message()));
        case Message.Event event -> messageBus.publish(List.of(event.message()));
      }
    }
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
