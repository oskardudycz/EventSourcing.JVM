package io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.groupcheckouts;

import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.Database;
import io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.core.EventStore;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e14_businessprocesses.solution2_immutableentities.groupcheckouts.GroupCheckoutFacade.GroupCheckoutCommand.*;

public class GroupCheckoutFacade {
  private final EventStore eventStore;

  public GroupCheckoutFacade(EventStore eventStore) {
    this.eventStore = eventStore;
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
