package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.time.OffsetDateTime;

public sealed interface PaymentEvent {
  record PaymentAuthorizationRequested(
    PaymentId paymentId,
    String referenceId,
    double amount,
    OffsetDateTime requestedAt
  ) implements PaymentEvent {
  }

  record PaymentAuthorized(
    PaymentId paymentId,
    OffsetDateTime authorizedAt,
    OffsetDateTime expiresAt
  ) implements PaymentEvent {
  }

  record PaymentCaptureRequested(
    PaymentId paymentId,
    double amount,
    OffsetDateTime requestedAt
  ) implements PaymentEvent {
  }

  record PaymentCaptured(
    PaymentId paymentId,
    double amount,
    OffsetDateTime capturedAt
  ) implements PaymentEvent {
  }

  record PaymentCaptureFailed(
    PaymentId paymentId,
    CaptureFailureReason reason,
    OffsetDateTime failedAt
  ) implements PaymentEvent {
  }

  record PaymentVoided(
    PaymentId paymentId,
    OffsetDateTime voidedAt) implements PaymentEvent {
  }

  record PaymentDeclined(
    PaymentId paymentId,
    DeclineReason reason,
    OffsetDateTime declinedAt) implements PaymentEvent {
  }

  record PaymentTimedOut(
    PaymentId paymentId,
    OffsetDateTime timedOutAt
  ) implements PaymentEvent {
  }

  record PaymentAuthorizationExpired(
    PaymentId paymentId,
    OffsetDateTime expiredAt
  ) implements PaymentEvent {
  }
}
