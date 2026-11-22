package io.eventdriven.ecommerce.payments;

import java.net.URI;
import java.time.OffsetDateTime;

// Opens checkout sessions, then refuses every call that follows the hold.
public class RefusingAfterAuthorizationPaymentGateway implements PaymentGateway {
    @Override
    public PaymentGateway.CheckoutSession createCheckoutSession(
            Payment.Id paymentId, double amount, URI successUrl, URI cancelUrl, OffsetDateTime expiresAt) {
        return new PaymentGateway.CheckoutSession(
                URI.create("https://checkout.example/c/pay/" + paymentId.tail()), expiresAt);
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
