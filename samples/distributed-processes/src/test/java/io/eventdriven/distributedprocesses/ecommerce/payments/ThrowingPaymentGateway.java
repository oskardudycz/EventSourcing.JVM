package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.net.URI;
import java.time.OffsetDateTime;

// Stands in for a provider that cannot be reached at all.
public class ThrowingPaymentGateway implements PaymentGateway {
    @Override
    public CheckoutSession createCheckoutSession(
            PaymentId paymentId, double amount, URI successUrl, URI cancelUrl, OffsetDateTime expiresAt) {
        throw new RuntimeException("Payment provider is unreachable");
    }

    @Override
    public void expireCheckoutSession(PaymentId paymentId) {
        throw new RuntimeException("Payment provider is unreachable");
    }

    @Override
    public void capture(PaymentId paymentId, double amount) {
        throw new RuntimeException("Payment provider is unreachable");
    }

    @Override
    public void cancel(PaymentId paymentId) {
        throw new RuntimeException("Payment provider is unreachable");
    }
}
