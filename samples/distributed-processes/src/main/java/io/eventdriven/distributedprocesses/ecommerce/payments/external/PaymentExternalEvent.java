package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;

import java.time.OffsetDateTime;

public sealed interface PaymentExternalEvent {
  record PaymentAuthorized(
    String referenceId,
    PaymentId paymentId,
    double amount,
    OffsetDateTime authorizedAt,
    OffsetDateTime expiresAt ) implements PaymentExternalEvent {
  }

  record PaymentCaptured(
    String referenceId,
    PaymentId paymentId,
    double amount,
    OffsetDateTime capturedAt ) implements PaymentExternalEvent {
  }

  record PaymentFailed(
    String referenceId,
    PaymentId paymentId,
    double amount,
    OffsetDateTime failedAt,
    Reason reason ) implements PaymentExternalEvent {
    public enum Reason {
      Declined,
      TimedOut,
      AuthorizationExpired
    }
  }
}
