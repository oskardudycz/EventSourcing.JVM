package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface PaymentsEvents {
  record PaymentRequested(
    UUID PaymentId,
    UUID OrderId,
    double Amount
  ) implements PaymentsEvents {
  }

  record PaymentCompleted(
    UUID PaymentId,
    OffsetDateTime CompletedAt
  ) implements PaymentsEvents {
  }

  record PaymentDiscarded(
    UUID PaymentId,
    DiscardReason DiscardReason,
    OffsetDateTime DiscardedAt) implements PaymentsEvents {
  }

  record PaymentTimedOut(
    UUID PaymentId,
    OffsetDateTime TimedOutAt
  ) implements PaymentsEvents {
  }
}
