package io.eventdriven.distributedprocesses.ecommerce.payments;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ConfirmPaymentCapture;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

// Opens checkout sessions and grants the capture straight away, and writes down every call it was given.
public class RecordingPaymentGateway implements PaymentGateway {
    public final List<String> calls = new ArrayList<>();
    private final MessageBus messageBus;

    public RecordingPaymentGateway(MessageBus messageBus) {
        this.messageBus = messageBus;
    }

    @Override
    public CheckoutSession createCheckoutSession(
            PaymentId paymentId, double amount, URI successUrl, URI cancelUrl, OffsetDateTime expiresAt) {
        calls.add("checkout-session:%s:%s:%s:%s:%s".formatted(paymentId, amount, successUrl, cancelUrl, expiresAt));
        return new CheckoutSession(URI.create("https://checkout.example/c/pay/" + paymentId.tail()), expiresAt);
    }

    @Override
    public void expireCheckoutSession(PaymentId paymentId) {
        calls.add("expire-checkout-session:%s".formatted(paymentId));
    }

    @Override
    public void capture(PaymentId paymentId, double amount) {
        calls.add("capture:%s:%s".formatted(paymentId, amount));
        messageBus.publish(List.of(new ConfirmPaymentCapture(paymentId)));
    }

    @Override
    public void cancel(PaymentId paymentId) {
        calls.add("cancel:%s".formatted(paymentId));
    }
}
