package io.eventdriven.distributedprocesses.ecommerce.payments.capture;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public class CaptureGatewayClient {
  private static final Logger logger = LoggerFactory.getLogger(CaptureGatewayClient.class);

  private final PaymentGateway paymentGateway;

  public CaptureGatewayClient(PaymentGateway paymentGateway) {
    this.paymentGateway = paymentGateway;
  }

  public void on(PaymentCaptured event) {
    tell("Capturing", event.paymentId(), () -> paymentGateway.capture(event.paymentId(), event.amount()));
  }

  public void on(PaymentRefunded event) {
    tell("Refunding", event.paymentId(), () -> paymentGateway.refund(event.paymentId()));
  }

  // The module has already recorded the outcome, so a provider that refuses the call leaves
  // nothing to decide here: the money is settled between the two sides by hand.
  private void tell(String attempt, PaymentId paymentId, Runnable callGateway) {
    try {
      callGateway.run();
    } catch (Exception failedCall) {
      logger.error("%s payment %s failed".formatted(attempt, paymentId), failedCall);
    }
  }
}
