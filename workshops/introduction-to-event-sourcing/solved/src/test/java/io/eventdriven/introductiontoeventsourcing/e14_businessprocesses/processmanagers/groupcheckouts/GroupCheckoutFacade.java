package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.CommandBus;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.Database;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventBus;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.core.Message;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.gueststayaccounts.GuestStayAccountEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.InitiateGroupCheckout;

public class GroupCheckoutFacade {
  private final Database database;
  private final EventBus eventBus;
  private final CommandBus commandBus;

  public GroupCheckoutFacade(Database database, EventBus eventBus, CommandBus commandBus) {
    this.database = database;
    this.eventBus = eventBus;
    this.commandBus = commandBus;
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    var groupCheckout = GroupCheckout.handle(command);

    database.store(command.groupCheckoutId(), groupCheckout);
    publish(groupCheckout.dequeueUncommittedMessages());
  }

  public void recordGuestCheckoutCompletion(GuestStayAccountEvent.GuestCheckedOut event) {
    if (event.groupCheckoutId() == null)
      return;

    var groupCheckout = database.get(GroupCheckout.class, event.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.on(event);

    database.store(event.groupCheckoutId(), groupCheckout);
    publish(groupCheckout.dequeueUncommittedMessages());
  }

  public void recordGuestCheckoutFailure(GuestStayAccountEvent.GuestCheckoutFailed event) {
    var groupCheckout = database.get(GroupCheckout.class, event.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.on(event);

    database.store(event.groupCheckoutId(), groupCheckout);
    publish(groupCheckout.dequeueUncommittedMessages());
  }

  void publish(Message[] messages) {
    for (Message message : messages) {
      switch (message) {
        case Message.Command command -> commandBus.send(new Object[]{command.message()});
        case Message.Event event -> eventBus.publish(new Object[]{event.message()});
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
