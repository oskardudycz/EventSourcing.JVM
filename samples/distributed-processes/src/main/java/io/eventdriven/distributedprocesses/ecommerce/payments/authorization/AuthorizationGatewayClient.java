package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizationGatewayClient {
    private static final Logger logger = LoggerFactory.getLogger(AuthorizationGatewayClient.class);

    private final PaymentGateway paymentGateway;

    public AuthorizationGatewayClient(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
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
    private void callGateway(String attempt, PaymentId paymentId, Consumer<PaymentId> call) {
        try {
            call.accept(paymentId);
        } catch (Exception failedCall) {
            logger.error("%s %s failed".formatted(attempt, paymentId), failedCall);
        }
    }
}
