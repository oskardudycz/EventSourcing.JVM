package io.eventdriven.ecommerce.payments.sandbox;

import static io.eventdriven.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;
import static io.eventdriven.ecommerce.payments.PaymentCommand.ConfirmPaymentCapture;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentGateway;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

// Stands in for the provider when the application runs: it serves its own checkout page and sends
// the commands that the provider's webhooks would cause.
public class SandboxPaymentGateway implements PaymentGateway {
    public enum Outcome {
        Paid,
        Declined,
        Expired,
        UnknownCard
    }

    public record Session(
            String id,
            Payment.Id paymentId,
            double amount,
            URI successUrl,
            URI cancelUrl,
            OffsetDateTime expiresAt,
            boolean expired) {}

    private static final String successCard = "4242424242424242";
    private static final String declinedCard = "4000000000000002";
    private static final Duration holdValidity = Duration.ofDays(7);

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final MessageBus messageBus;
    private final Supplier<OffsetDateTime> now;

    public SandboxPaymentGateway(MessageBus messageBus, Supplier<OffsetDateTime> now) {
        this.messageBus = messageBus;
        this.now = now;
    }

    @Override
    public PaymentGateway.CheckoutSession createCheckoutSession(
            Payment.Id paymentId, double amount, URI successUrl, URI cancelUrl, OffsetDateTime expiresAt) {
        var id = UUID.randomUUID().toString();
        sessions.put(id, new Session(id, paymentId, amount, successUrl, cancelUrl, expiresAt, false));

        return new PaymentGateway.CheckoutSession(URI.create("/sandbox/checkout/" + id), expiresAt);
    }

    @Override
    public void expireCheckoutSession(Payment.Id paymentId) {
        sessions.replaceAll((id, session) -> session.paymentId().equals(paymentId)
                ? new Session(
                        id,
                        paymentId,
                        session.amount(),
                        session.successUrl(),
                        session.cancelUrl(),
                        session.expiresAt(),
                        true)
                : session);
    }

    @Override
    public void capture(Payment.Id paymentId, double amount) {
        messageBus.publish(List.of(new ConfirmPaymentCapture(paymentId)));
    }

    @Override
    public void cancel(Payment.Id paymentId) {}

    public Optional<Session> session(String id) {
        return Optional.ofNullable(sessions.get(id));
    }

    public Outcome pay(Session session, String cardNumber) {
        if (session.expired() || !now.get().isBefore(session.expiresAt())) return Outcome.Expired;

        return switch (cardNumber.replaceAll("\\s", "")) {
            case successCard -> {
                messageBus.publish(List.of(new ConfirmPaymentAuthorization(
                        session.paymentId(), now.get().plus(holdValidity))));
                yield Outcome.Paid;
            }
            case declinedCard -> Outcome.Declined;
            default -> Outcome.UnknownCard;
        };
    }
}
