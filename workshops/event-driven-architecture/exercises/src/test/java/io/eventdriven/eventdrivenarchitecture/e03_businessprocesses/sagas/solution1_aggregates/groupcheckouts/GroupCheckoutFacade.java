package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.groupcheckouts;

import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.Database;
import io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.core.EventBus;

import static io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas.solution1_aggregates.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GroupCheckoutFacade {
  private final Database database;
  private final EventBus eventBus;

  public GroupCheckoutFacade(Database database, EventBus eventBus) {
    this.database = database;
    this.eventBus = eventBus;
  }

  public void initiateGroupCheckout(InitiateGroupCheckout command) {
    throw new RuntimeException("TODO: implement me");
  }

  public void recordGuestCheckoutCompletion(RecordGuestCheckoutCompletion command)
  {
    throw new RuntimeException("TODO: implement me");
  }

  public void recordGuestCheckoutFailure(RecordGuestCheckoutFailure command)
  {
    throw new RuntimeException("TODO: implement me");
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
