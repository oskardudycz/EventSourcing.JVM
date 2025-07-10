package io.eventdriven.introductiontoeventsourcing.e13_entities_definition.solution2_immutableentities.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e13_entities_definition.core.EventStore;

import java.time.OffsetDateTime;
import java.util.UUID;

public class GroupCheckoutFacade {
  private final EventStore eventStore;

  public GroupCheckoutFacade(EventStore eventStore) {
    this.eventStore = eventStore;
  }

  public void initiateGroupCheckout(GroupCheckoutCommand.InitiateGroupCheckout command) {
    throw new RuntimeException("TODO: implement me");
  }

  public void recordGuestCheckoutCompletion(GroupCheckoutCommand.RecordGuestCheckoutCompletion command)
  {
    throw new RuntimeException("TODO: implement me");
  }

  public void recordGuestCheckoutFailure(GroupCheckoutCommand.RecordGuestCheckoutFailure command)
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
