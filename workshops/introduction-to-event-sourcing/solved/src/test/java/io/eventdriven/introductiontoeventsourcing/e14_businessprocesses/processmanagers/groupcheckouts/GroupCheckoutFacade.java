package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.CommandBus;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.Database;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventStore;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.core.Message;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.gueststayaccounts.GuestStayAccountEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.processmanagers.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.InitiateGroupCheckout;

public class GroupCheckoutFacade {
  private final Database database;
  private final EventStore eventStore;
  private final CommandBus commandBus;

  public GroupCheckoutFacade(Database database, EventStore eventStore, CommandBus commandBus) {
    this.database = database;
    this.eventStore = eventStore;
    this.commandBus = commandBus;
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    var groupCheckout = GroupCheckout.handle(command);

    database.store(command.groupCheckoutId(), groupCheckout);
    appendToStream(groupCheckout.dequeueUncommittedMessages());
  }

  public void recordGuestCheckoutCompletion(GuestStayAccountEvent.GuestCheckedOut event) {
    if (event.groupCheckoutId() == null)
      return;

    var groupCheckout = database.get(GroupCheckout.class, event.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.on(event);

    database.store(event.groupCheckoutId(), groupCheckout);
    appendToStream(groupCheckout.dequeueUncommittedMessages());
  }

  public void recordGuestCheckoutFailure(GuestStayAccountEvent.GuestCheckoutFailed event) {
    var groupCheckout = database.get(GroupCheckout.class, event.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.on(event);

    database.store(event.groupCheckoutId(), groupCheckout);
    appendToStream(groupCheckout.dequeueUncommittedMessages());
  }

  void appendToStream(Message[] messages) {
    for (Message message : messages) {
      switch (message) {
        case Message.Command command -> commandBus.send(new Object[]{command.message()});
        case Message.Event event -> eventStore.appendToStream(new Object[]{event.message()});
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
