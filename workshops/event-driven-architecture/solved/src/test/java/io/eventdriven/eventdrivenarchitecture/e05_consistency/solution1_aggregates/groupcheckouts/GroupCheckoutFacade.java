package io.eventdriven.eventdrivenarchitecture.e05_consistency.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e05_consistency.core.Database;
import io.eventdriven.eventdrivenarchitecture.e05_consistency.core.MessageBus;
import io.eventdriven.eventdrivenarchitecture.e05_consistency.core.Outbox;

import static io.eventdriven.eventdrivenarchitecture.e05_consistency.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GroupCheckoutFacade {
  private final Database database;
  private final Database.Collection<GroupCheckout> collection;
  private final MessageBus messageBus;

  public GroupCheckoutFacade(Database database, MessageBus messageBus) {
    this.database = database;
    this.collection = database.collection(GroupCheckout.class);
    this.messageBus = messageBus;
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    if (collection.get(command.groupCheckoutId()).isPresent())
      return;

    var groupCheckout = GroupCheckout.initiate(
      command.groupCheckoutId(),
      command.clerkId(),
      command.guestStayIds(),
      command.now()
    );

    Outbox.storeAndPublish(database, messageBus, groupCheckout.dequeueUncommittedEvents(),
      transaction -> transaction.collection(GroupCheckout.class).store(command.groupCheckoutId(), groupCheckout));
  }

  public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command)
  {
    var groupCheckout = collection.get(command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.recordGuestCheckoutCompletion(command.guestStayId(), command.completedAt());

    Outbox.storeAndPublish(database, messageBus, groupCheckout.dequeueUncommittedEvents(),
      transaction -> transaction.collection(GroupCheckout.class).store(command.groupCheckoutId(), groupCheckout));
  }

  public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command)
  {
    var groupCheckout = collection.get(command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.recordGuestCheckoutFailure(command.guestStayId(), command.failedAt());

    Outbox.storeAndPublish(database, messageBus, groupCheckout.dequeueUncommittedEvents(),
      transaction -> transaction.collection(GroupCheckout.class).store(command.groupCheckoutId(), groupCheckout));
  }

  public sealed interface GroupCheckoutCommand {
    record InitiateGroupCheckout(
      UUID groupCheckoutId,
      UUID clerkId,
      UUID[] guestStayIds,
      OffsetDateTime now
    ) implements GroupCheckoutCommand {
    }

    record RecordGuestCheckoutCompletion(
      UUID groupCheckoutId,
      UUID guestStayId,
      OffsetDateTime completedAt
    ) implements GroupCheckoutCommand {
    }

    record RecordGuestCheckoutFailure(
      UUID groupCheckoutId,
      UUID guestStayId,
      OffsetDateTime failedAt
    ) implements GroupCheckoutCommand {
    }
  }
}
