package io.eventdriven.distributedprocesses.ecommerce.payments;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

import java.time.OffsetDateTime;
import java.util.Optional;

public record Payment(
        PaymentId id,
        String referenceId,
        double amount,
        Status status,
        OffsetDateTime timesOutAt,
        OffsetDateTime captureBefore,
        CheckoutSession checkoutSession) {

    enum Status {
        Pending,
        Authorized,
        CaptureRequested,
        Captured,
        Canceled,
        Failed
    }

    public static Payment initial() {
        return new Payment(null, null, 0, null, null, null, null);
    }

    public boolean exists() {
        return status != null;
    }

    public boolean isPending() {
        return status == Status.Pending;
    }

    public Optional<CheckoutSession> openCheckoutSession(OffsetDateTime now) {
        return checkoutSession != null && now.isBefore(checkoutSession.expiresAt())
                ? Optional.of(checkoutSession)
                : Optional.empty();
    }

    public static Payment evolve(Payment state, PaymentEvent event) {
        return switch (event) {
            case PaymentAuthorizationRequested requested ->
                new Payment(
                        requested.paymentId(),
                        requested.referenceId(),
                        requested.amount(),
                        Status.Pending,
                        requested.timesOutAt(),
                        state.captureBefore,
                        state.checkoutSession);
            case CheckoutSessionCreated created ->
                new Payment(
                        state.id,
                        state.referenceId,
                        state.amount,
                        state.status,
                        state.timesOutAt,
                        state.captureBefore,
                        new CheckoutSession(created.url(), created.expiresAt()));
            case PaymentAuthorized authorized ->
                new Payment(
                        state.id,
                        state.referenceId,
                        state.amount,
                        Status.Authorized,
                        state.timesOutAt,
                        authorized.captureBefore(),
                        state.checkoutSession);
            case PaymentCaptureRequested ignored -> state.withStatus(Status.CaptureRequested);
            case PaymentCaptured ignored -> state.withStatus(Status.Captured);
            case PaymentCaptureFailed ignored -> state.withStatus(Status.Failed);
            case PaymentCanceled ignored -> state.withStatus(Status.Canceled);
            case PaymentTimedOut ignored -> state.withStatus(Status.Failed);
            case PaymentAuthorizationExpired ignored -> state.withStatus(Status.Failed);
        };
    }

    private Payment withStatus(Status status) {
        return new Payment(id, referenceId, amount, status, timesOutAt, captureBefore, checkoutSession);
    }
}
