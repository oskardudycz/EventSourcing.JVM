package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;

import java.time.OffsetDateTime;

public sealed interface PaymentExternalEvent {
  record PaymentAuthorized(
    String referenceId,
    PaymentId paymentId,
    OffsetDateTime authorizedAt ) implements PaymentExternalEvent {
  }

  record PaymentCaptured(
    String referenceId,
    PaymentId paymentId,
    OffsetDateTime capturedAt ) implements PaymentExternalEvent {
  }

  record PaymentFailed(
    String referenceId,
    PaymentId paymentId,
    OffsetDateTime failedAt ) implements PaymentExternalEvent {
  }
}
