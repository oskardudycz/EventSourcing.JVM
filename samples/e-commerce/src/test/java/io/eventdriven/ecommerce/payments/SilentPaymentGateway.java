package io.eventdriven.ecommerce.payments;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

// Stands in for a provider that opens checkout sessions and has taken the capture but not answered it yet.
public class SilentPaymentGateway implements PaymentGateway {
    public final List<Payment.Id> checkoutSessionsCreated = new ArrayList<>();
    public final List<Payment.Id> canceled = new ArrayList<>();
    public final List<Payment.Id> expiredCheckoutSessions = new ArrayList<>();

    @Override
    public PaymentGateway.CheckoutSession createCheckoutSession(
            Payment.Id paymentId, double amount, URI successUrl, URI cancelUrl, OffsetDateTime expiresAt) {
        checkoutSessionsCreated.add(paymentId);
        return new PaymentGateway.CheckoutSession(
                URI.create("https://checkout.example/c/pay/" + paymentId.tail()), expiresAt);
    }

    @Override
    public void expireCheckoutSession(Payment.Id paymentId) {
        expiredCheckoutSessions.add(paymentId);
    }

    @Override
    public void capture(Payment.Id paymentId, double amount) {}

    @Override
    public void cancel(Payment.Id paymentId) {
        canceled.add(paymentId);
    }
}
