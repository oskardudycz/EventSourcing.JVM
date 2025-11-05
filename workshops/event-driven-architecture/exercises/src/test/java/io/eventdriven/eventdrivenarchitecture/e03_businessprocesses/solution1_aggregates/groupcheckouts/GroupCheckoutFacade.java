package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GroupCheckoutFacade {
  private final Database.Collection<GroupCheckout> collection;
  private final EventBus eventBus;

  public GroupCheckoutFacade(Database database, EventBus eventBus) {
    this.collection = database.collection(GroupCheckout.class);
    this.eventBus = eventBus;
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
    Object[] events = new Object[]{};

    collection.store(command.guestStayId(), account);
    eventBus.publish(events);

    throw new RuntimeException("TODO: Fill the implementation calling your entity/aggregate");
  }

  public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command)
  {
    var account = collection.get(command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    // TODO: Fill the implementation calling your entity/aggregate
    // account.doSomething;
    Object[] events = new Object[]{};

    collection.store(command.guestStayId(), account);
    eventBus.publish(events);

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
