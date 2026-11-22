package io.eventdriven.ecommerce.payments;

import java.net.URI;
import java.time.OffsetDateTime;

// Stands in for a provider that cannot be reached at all.
public class ThrowingPaymentGateway implements PaymentGateway {
    @Override
    public PaymentGateway.CheckoutSession createCheckoutSession(
            Payment.Id paymentId, double amount, URI successUrl, URI cancelUrl, OffsetDateTime expiresAt) {
        throw new RuntimeException("Payment provider is unreachable");
    }

    @Override
    public void expireCheckoutSession(Payment.Id paymentId) {
        throw new RuntimeException("Payment provider is unreachable");
    }

    @Override
    public void capture(Payment.Id paymentId, double amount) {
        throw new RuntimeException("Payment provider is unreachable");
    }

    @Override
    public void cancel(Payment.Id paymentId) {
        throw new RuntimeException("Payment provider is unreachable");
    }
}
