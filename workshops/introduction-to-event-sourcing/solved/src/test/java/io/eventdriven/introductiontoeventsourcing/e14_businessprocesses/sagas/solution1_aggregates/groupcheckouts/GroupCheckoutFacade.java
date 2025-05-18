package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution1_aggregates.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.Database;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventStore;

import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.sagas.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GroupCheckoutFacade {
  private final Database database;
  private final EventStore eventStore;

  public GroupCheckoutFacade(Database database, EventStore eventStore) {
    this.database = database;
    this.eventStore = eventStore;
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    var groupCheckout = GroupCheckout.initiate(
      command.groupCheckoutId(),
      command.clerkId(),
      command.guestStayIds(),
      command.now()
    );

    database.store(command.groupCheckoutId(), groupCheckout);
    eventStore.appendToStream(groupCheckout.dequeueUncommittedEvents());
  }

  public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command)
  {
    var groupCheckout = database.get(GroupCheckout.class, command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.recordGuestCheckoutCompletion(command.guestStayId(), command.completedAt());

    database.store(command.groupCheckoutId(), groupCheckout);
    eventStore.appendToStream(groupCheckout.dequeueUncommittedEvents());
  }

  public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command)
  {
    var groupCheckout = database.get(GroupCheckout.class, command.groupCheckoutId())
      .orElseThrow(() -> new IllegalStateException("Entity not found"));

    groupCheckout.recordGuestCheckoutFailure(command.guestStayId(), command.failedAt());

    database.store(command.groupCheckoutId(), groupCheckout);
    eventStore.appendToStream(groupCheckout.dequeueUncommittedEvents());
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
