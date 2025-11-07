package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.sagas_rabbitmq.solution1_aggregates.groupcheckouts;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public sealed interface GroupCheckoutEvent {
  record GroupCheckoutInitiated(
    UUID groupCheckoutId,
    UUID clerkId,
    UUID[] guestStayAccountIds,
    OffsetDateTime initiatedAt
  ) implements GroupCheckoutEvent {
  }

  record GuestCheckoutCompletionRecorded(
    UUID groupCheckoutId,
    UUID guestStayAccountId,
    OffsetDateTime completedAt
  ) implements GroupCheckoutEvent {
  }

  record GuestCheckoutFailureRecorded(
    UUID groupCheckoutId,
    UUID guestStayAccountId,
    OffsetDateTime failedAt
  ) implements GroupCheckoutEvent {
  }

  record GroupCheckoutCompleted(
    UUID groupCheckoutId,
    UUID[] completedCheckouts,
    OffsetDateTime completedAt
  ) implements GroupCheckoutEvent {

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      GroupCheckoutCompleted that = (GroupCheckoutCompleted) o;
      return Objects.equals(groupCheckoutId, that.groupCheckoutId) && Objects.deepEquals(completedCheckouts, that.completedCheckouts) && Objects.equals(completedAt, that.completedAt);
    }

    @Override
    public int hashCode() {
      return Objects.hash(groupCheckoutId, Arrays.hashCode(completedCheckouts), completedAt);
    }
  }

  record GroupCheckoutFailed(
    UUID groupCheckoutId,
    UUID[] completedCheckouts,
    UUID[] failedCheckouts,
    OffsetDateTime failedAt
  ) implements GroupCheckoutEvent {

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      GroupCheckoutFailed that = (GroupCheckoutFailed) o;
      return Objects.equals(groupCheckoutId, that.groupCheckoutId) && Objects.deepEquals(failedCheckouts, that.failedCheckouts) && Objects.equals(failedAt, that.failedAt) && Objects.deepEquals(completedCheckouts, that.completedCheckouts);
    }

    @Override
    public int hashCode() {
      return Objects.hash(groupCheckoutId, Arrays.hashCode(completedCheckouts), Arrays.hashCode(failedCheckouts), failedAt);
    }
  }
}
