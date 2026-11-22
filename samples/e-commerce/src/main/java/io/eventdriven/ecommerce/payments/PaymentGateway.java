package io.eventdriven.ecommerce.payments;

import java.net.URI;
import java.time.OffsetDateTime;

// The customer pays on the provider's checkout page, and the provider confirms the authorization
// later through a webhook. A real provider also only accepts the capture here and confirms it the
// same way. In this sample both webhooks are modelled by the implementation sending a command back.
// Canceling and expiring a checkout session do not answer back.
public interface PaymentGateway {
    CheckoutSession createCheckoutSession(
            Payment.Id paymentId, double amount, URI successUrl, URI cancelUrl, OffsetDateTime expiresAt);

    void expireCheckoutSession(Payment.Id paymentId);

    void capture(Payment.Id paymentId, double amount);

    void cancel(Payment.Id paymentId);

    record CheckoutSession(URI url, OffsetDateTime expiresAt) {}

    class GatewayException extends RuntimeException {
        public GatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
