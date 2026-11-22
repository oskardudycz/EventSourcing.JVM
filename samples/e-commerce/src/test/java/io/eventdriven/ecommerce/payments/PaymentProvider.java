package io.eventdriven.ecommerce.payments;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.ecommerce.payments.PaymentCommand.ConfirmPaymentCapture;
import io.eventdriven.testing.api.ApiE2ESpecification.TestAction;
import io.eventdriven.testing.api.ApiE2ESpecification.TestStep;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PaymentProvider implements PaymentGateway {
    private final MessageBus messageBus;
    private final Map<Payment.Id, List<String>> calls = new ConcurrentHashMap<>();
    private final Set<Payment.Id> unreachable = ConcurrentHashMap.newKeySet();
    private final Set<Payment.Id> refusingAfterAuthorization = ConcurrentHashMap.newKeySet();
    private final Set<Payment.Id> refusingFirstCapture = ConcurrentHashMap.newKeySet();

    public PaymentProvider(MessageBus messageBus) {
        this.messageBus = messageBus;
    }

    public static URI checkoutPageOf(Payment.Id paymentId) {
        return URI.create("https://checkout.example/c/pay/" + paymentId.tail());
    }

    public TestStep isUnreachableFor(Payment.Id paymentId) {
        return (TestAction) () -> unreachable.add(paymentId);
    }

    public TestStep refusesEverythingAfterAuthorizing(Payment.Id paymentId) {
        return (TestAction) () -> refusingAfterAuthorization.add(paymentId);
    }

    public TestStep refusesTheFirstCaptureOf(Payment.Id paymentId) {
        return (TestAction) () -> refusingFirstCapture.add(paymentId);
    }

    public List<String> callsFor(Payment.Id paymentId) {
        return List.copyOf(calls.getOrDefault(paymentId, List.of()));
    }

    @Override
    public CheckoutSession createCheckoutSession(
            Payment.Id paymentId, double amount, URI successUrl, URI cancelUrl, OffsetDateTime expiresAt) {
        record(paymentId, "checkout-session:%s:%s:%s:%s".formatted(amount, successUrl, cancelUrl, expiresAt));
        failIf(unreachable.contains(paymentId));

        return new CheckoutSession(checkoutPageOf(paymentId), expiresAt);
    }

    @Override
    public void expireCheckoutSession(Payment.Id paymentId) {
        record(paymentId, "expire-checkout-session");
        failIf(unreachable.contains(paymentId) || refusingAfterAuthorization.contains(paymentId));
    }

    @Override
    public void capture(Payment.Id paymentId, double amount) {
        record(paymentId, "capture:%s".formatted(amount));
        failIf(unreachable.contains(paymentId)
                || refusingAfterAuthorization.contains(paymentId)
                || refusingFirstCapture.remove(paymentId));

        messageBus.publish(List.of(new ConfirmPaymentCapture(paymentId)));
    }

    @Override
    public void cancel(Payment.Id paymentId) {
        record(paymentId, "cancel");
        failIf(unreachable.contains(paymentId) || refusingAfterAuthorization.contains(paymentId));
    }

    private void record(Payment.Id paymentId, String call) {
        calls.computeIfAbsent(paymentId, id -> new CopyOnWriteArrayList<>()).add(call);
    }

    private static void failIf(boolean refused) {
        if (refused) throw new RuntimeException("Payment provider is unreachable");
    }
}
