package io.eventdriven.ecommerce.payments;

import java.net.URI;
import java.time.OffsetDateTime;

public sealed interface PaymentEvent {
    record PaymentAuthorizationRequested(
            Payment.Id paymentId,
            String referenceId,
            double amount,
            OffsetDateTime requestedAt,
            OffsetDateTime timesOutAt)
            implements PaymentEvent {}

    record CheckoutSessionCreated(Payment.Id paymentId, URI url, OffsetDateTime expiresAt) implements PaymentEvent {}

    record PaymentAuthorized(Payment.Id paymentId, OffsetDateTime authorizedAt, OffsetDateTime captureBefore)
            implements PaymentEvent {}

    record PaymentCaptureRequested(Payment.Id paymentId, double amount, OffsetDateTime requestedAt)
            implements PaymentEvent {}

    record PaymentCaptured(Payment.Id paymentId, double amount, OffsetDateTime capturedAt) implements PaymentEvent {}

    record PaymentCaptureFailed(Payment.Id paymentId, Payment.CaptureFailureReason reason, OffsetDateTime failedAt)
            implements PaymentEvent {}

    record PaymentCanceled(Payment.Id paymentId, OffsetDateTime canceledAt) implements PaymentEvent {}

    record PaymentTimedOut(Payment.Id paymentId, OffsetDateTime timedOutAt) implements PaymentEvent {}

    record PaymentAuthorizationExpired(Payment.Id paymentId, OffsetDateTime expiredAt) implements PaymentEvent {}
}
