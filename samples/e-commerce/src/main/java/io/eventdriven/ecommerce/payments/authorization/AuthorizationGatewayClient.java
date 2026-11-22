package io.eventdriven.ecommerce.payments.authorization;

import static io.eventdriven.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.processing.Reactor;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentGateway;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizationGatewayClient implements Reactor {
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationGatewayClient.class);

    private final PaymentGateway paymentGateway;

    public AuthorizationGatewayClient(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(PaymentCanceled.class, PaymentTimedOut.class);
    }

    @Override
    public void handle(MessageEnvelope<Object> envelope) {
        switch (envelope.message()) {
            case PaymentCanceled event -> on(event);
            case PaymentTimedOut event -> on(event);
            default -> throw new IllegalArgumentException("Unexpected message %s".formatted(envelope.message()));
        }
    }

    public void on(PaymentCanceled event) {
        callGateway("Canceling payment", event.paymentId(), paymentGateway::cancel);
    }

    public void on(PaymentTimedOut event) {
        callGateway(
                "Expiring the checkout session of payment", event.paymentId(), paymentGateway::expireCheckoutSession);
    }

    // The module has already recorded the outcome, so a provider that refuses the call leaves
    // nothing to decide here: the money is settled between the two sides by hand.
    private void callGateway(String attempt, Payment.Id paymentId, Consumer<Payment.Id> call) {
        try {
            call.accept(paymentId);
        } catch (Exception failedCall) {
            logger.error("%s %s failed".formatted(attempt, paymentId), failedCall);
        }
    }
}
