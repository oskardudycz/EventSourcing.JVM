package io.eventdriven.ecommerce.payments;

import static io.eventdriven.ecommerce.payments.PaymentEvent.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.eventdriven.core.identifiers.EntityId;
import io.eventdriven.core.identifiers.Urns;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public record Payment(
        Id id,
        String referenceId,
        double amount,
        Status status,
        OffsetDateTime timesOutAt,
        OffsetDateTime captureBefore,
        PaymentGateway.CheckoutSession checkoutSession) {

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

    public Optional<PaymentGateway.CheckoutSession> openCheckoutSession(OffsetDateTime now) {
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
                        new PaymentGateway.CheckoutSession(created.url(), created.expiresAt()));
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

    @Embeddable
    public record Id(@JsonValue String value) implements EntityId {
        private static final String type = "payment";

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Id {
            Urns.requireType(value, type);
        }

        public static Id of(UUID id) {
            return new Id(Urns.of("ecommerce", type, id));
        }

        public static Id derivedFrom(String sourceUrn) {
            return new Id(Urns.derive(sourceUrn, type));
        }

        public static Id derivedFrom(String sourceUrn, int attempt) {
            if (attempt == 1) return derivedFrom(sourceUrn);

            return new Id(
                    Urns.of(Urns.namespaceOf(sourceUrn), type, "%s-%d".formatted(Urns.tailOf(sourceUrn), attempt)));
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public enum CaptureFailureReason {
        UnexpectedError
    }

    public interface Validity {
        OffsetDateTime timesOutAt(OffsetDateTime requestedAt);

        static Validity of(Duration validityTime) {
            return requestedAt -> requestedAt.plus(validityTime);
        }
    }
}
