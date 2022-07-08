package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface PaymentEvent {
  record PaymentRequested(
    UUID paymentId,
    UUID orderId,
    double amount
  ) implements PaymentEvent {
  }

  record PaymentCompleted(
    UUID paymentId,
    OffsetDateTime completedAt
  ) implements PaymentEvent {
  }

  record PaymentDiscarded(
    UUID paymentId,
    DiscardReason discardReason,
    OffsetDateTime discardedAt) implements PaymentEvent {
  }

  record PaymentTimedOut(
    UUID paymentId,
    OffsetDateTime timedOutAt
  ) implements PaymentEvent {
  }
}
