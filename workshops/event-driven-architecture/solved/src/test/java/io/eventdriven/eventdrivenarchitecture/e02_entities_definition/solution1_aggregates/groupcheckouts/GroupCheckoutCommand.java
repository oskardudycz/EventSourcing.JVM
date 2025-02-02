package io.eventdriven.eventdrivenarchitecture.e02_entities_definition.solution1_aggregates.groupcheckouts;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface GroupCheckoutCommand {
  record InitiateGroupCheckout(
    UUID groupCheckoutId,
    UUID clerkId,
    UUID[] guestStayAccountIds,
    OffsetDateTime now
  ) implements GroupCheckoutCommand {
  }
}
