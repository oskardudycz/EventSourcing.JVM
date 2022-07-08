package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface PaymentExternalEvent {
  record PaymentFinalized(
    UUID orderId,
    UUID paymentId,
    double amount,
    OffsetDateTime finalizedAt ) implements PaymentExternalEvent {
  }

  record PaymentFailed(
    UUID orderId,
    UUID paymentId,
    double amount,
    OffsetDateTime failedAt,
    Reason reason ) implements PaymentExternalEvent {
    enum Reason{
      Discarded,
      TimedOut
    }
  }
}
