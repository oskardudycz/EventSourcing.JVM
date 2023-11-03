package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface PaymentCommand {
  record RequestPayment(UUID paymentId, UUID orderId, double amount) implements PaymentCommand {}

  record CompletePayment(UUID paymentId) implements PaymentCommand {}

  record DiscardPayment(UUID paymentId, DiscardReason discardReason) implements PaymentCommand {}

  record TimeOutPayment(UUID paymentId, OffsetDateTime timedOutAt) implements PaymentCommand {}
}
