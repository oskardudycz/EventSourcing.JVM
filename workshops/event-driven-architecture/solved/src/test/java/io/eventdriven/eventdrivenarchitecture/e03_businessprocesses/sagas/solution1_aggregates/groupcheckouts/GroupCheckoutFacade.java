package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GroupCheckoutFacade {
  private final Database.Collection<GroupCheckout> collection;
  private final EventBus eventBus;

  public GroupCheckoutFacade(Database.Collection<GroupCheckout> collection, EventBus eventBus) {
    this.collection = collection;
    this.eventBus = eventBus;
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    var groupCheckout = GroupCheckout.initiate(
      command.groupCheckoutId(),
      command.clerkId(),
      command.guestStayIds(),
      command.now()
    );

    collection.store(command.groupCheckoutId(), groupCheckout);
    eventBus.publish(groupCheckout.dequeueUncommittedEvents());
  }

  public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command)
  {
    var groupCheckout = collection.get(command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.recordGuestCheckoutCompletion(command.guestStayId(), command.completedAt());

    collection.store(command.groupCheckoutId(), groupCheckout);
    eventBus.publish(groupCheckout.dequeueUncommittedEvents());
  }

  public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command)
  {
    var groupCheckout = collection.get(command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.recordGuestCheckoutFailure(command.guestStayId(), command.failedAt());

    collection.store(command.groupCheckoutId(), groupCheckout);
    eventBus.publish(groupCheckout.dequeueUncommittedEvents());
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
