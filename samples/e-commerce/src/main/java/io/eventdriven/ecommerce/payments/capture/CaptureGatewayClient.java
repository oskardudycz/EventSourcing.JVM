package io.eventdriven.ecommerce.payments.capture;

import static io.eventdriven.ecommerce.payments.PaymentCommand.FailPaymentCapture;
import static io.eventdriven.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.ecommerce.payments.Payment;
import io.eventdriven.ecommerce.payments.PaymentGateway;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CaptureGatewayClient {
    private static final Logger logger = LoggerFactory.getLogger(CaptureGatewayClient.class);

    private final PaymentGateway paymentGateway;
    private final MessageBus messageBus;

    public CaptureGatewayClient(PaymentGateway paymentGateway, MessageBus messageBus) {
        this.paymentGateway = paymentGateway;
        this.messageBus = messageBus;
    }

    public void on(PaymentCaptureRequested event) {
        try {
            paymentGateway.capture(event.paymentId(), event.amount());
        } catch (Exception failedCapture) {
            logger.error("Capturing payment %s failed".formatted(event.paymentId()), failedCapture);

            messageBus.publish(
                    List.of(new FailPaymentCapture(event.paymentId(), Payment.CaptureFailureReason.UnexpectedError)));
        }
    }
}
