package io.eventdriven.ecommerce.payments;

import static io.eventdriven.ecommerce.payments.PaymentCommand.ConfirmPaymentCapture;

import io.eventdriven.core.messaging.MessageBus;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;

// Refuses the first capture, then grants the captures that follow.
public class FailingFirstCapturePaymentGateway implements PaymentGateway {
    private final MessageBus messageBus;
    private boolean refusedOnce;

    public FailingFirstCapturePaymentGateway(MessageBus messageBus) {
        this.messageBus = messageBus;
    }

    @Override
    public PaymentGateway.CheckoutSession createCheckoutSession(
            Payment.Id paymentId, double amount, URI successUrl, URI cancelUrl, OffsetDateTime expiresAt) {
        return new PaymentGateway.CheckoutSession(
                URI.create("https://checkout.example/c/pay/" + paymentId.tail()), expiresAt);
    }

    @Override
    public void expireCheckoutSession(Payment.Id paymentId) {}

    @Override
    public void capture(Payment.Id paymentId, double amount) {
        if (!refusedOnce) {
            refusedOnce = true;
            throw new RuntimeException("Payment provider is unreachable");
        }

        messageBus.publish(List.of(new ConfirmPaymentCapture(paymentId)));
    }

    @Override
    public void cancel(Payment.Id paymentId) {}
}
