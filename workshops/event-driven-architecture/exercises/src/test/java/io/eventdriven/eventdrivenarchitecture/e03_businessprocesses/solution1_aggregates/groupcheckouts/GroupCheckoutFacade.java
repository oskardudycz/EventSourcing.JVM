package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.MessageBus;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class GroupCheckoutFacade {
  private final Database.Collection<GroupCheckout> collection;
  private final MessageBus messageBus;

  public GroupCheckoutFacade(Database database, MessageBus messageBus) {
    this.collection = database.collection(GroupCheckout.class);
    this.messageBus = messageBus;
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    // TODO: Fill the implementation calling your entity/aggregate
    throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
  }

  public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command)
  {
    var account = collection.get(command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    // TODO: Fill the implementation calling your entity/aggregate
    // account.doSomething;
    var events = List.of();

    collection.store(command.guestStayId(), account);
    messageBus.publish(events);

    throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
  }

  public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command)
  {
    var account = collection.get(command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    // TODO: Fill the implementation calling your entity/aggregate
    // account.doSomething;
    var events = List.of();

    collection.store(command.guestStayId(), account);
    messageBus.publish(events);

    throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
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
