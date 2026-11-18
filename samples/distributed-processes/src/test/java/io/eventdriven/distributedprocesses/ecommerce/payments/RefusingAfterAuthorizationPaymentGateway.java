package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.net.URI;
import java.time.OffsetDateTime;

// Opens checkout sessions, then refuses every call that follows the hold.
public class RefusingAfterAuthorizationPaymentGateway implements PaymentGateway {
    @Override
    public CheckoutSession createCheckoutSession(
            PaymentId paymentId, double amount, URI successUrl, URI cancelUrl, OffsetDateTime expiresAt) {
        return new CheckoutSession(URI.create("https://checkout.example/c/pay/" + paymentId.tail()), expiresAt);
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
