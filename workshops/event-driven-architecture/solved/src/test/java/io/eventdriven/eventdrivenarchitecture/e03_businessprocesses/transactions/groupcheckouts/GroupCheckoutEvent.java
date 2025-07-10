package io.eventdriven.eventdrivenarchitecture.e03_businessprocesses.transactions.groupcheckouts;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface GroupCheckoutEvent {

  record GroupCheckoutCompleted(
    UUID groupCheckoutId,
    UUID[] completedCheckouts,
    OffsetDateTime completedAt
  ) implements GroupCheckoutEvent {
  }

  record GroupCheckoutFailed(
    UUID groupCheckoutId,
    UUID[] completedCheckouts,
    UUID[] failedCheckouts,
    OffsetDateTime failedAt
  ) implements GroupCheckoutEvent {
  }
}
