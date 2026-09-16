package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.DeclinePayment;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public class PaymentGatewayClient {
  private static final Logger logger = LoggerFactory.getLogger(PaymentGatewayClient.class);

  private final PaymentGateway paymentGateway;
  private final CommandBus commandBus;

  public PaymentGatewayClient(PaymentGateway paymentGateway, CommandBus commandBus) {
    this.paymentGateway = paymentGateway;
    this.commandBus = commandBus;
  }

  public void on(PaymentAuthorizationRequested event) {
    try {
      paymentGateway.authorize(event.paymentId(), event.amount());
    } catch (Exception failedAuthorization) {
      // An authorisation that did not go through is a business outcome, not a crash: rethrowing
      // here would leave the payment pending and freeze the process waiting for it.
      logger.error(
        "Authorizing payment %s failed".formatted(event.paymentId()),
        failedAuthorization
      );

      commandBus.send(new DeclinePayment(event.paymentId(), DeclineReason.UnexpectedError));
    }
  }

  public void on(PaymentCaptured event) {
    tell("Capturing", event.paymentId(), () -> paymentGateway.capture(event.paymentId(), event.amount()));
  }

  public void on(PaymentVoided event) {
    tell("Voiding", event.paymentId(), () -> paymentGateway.voidAuthorization(event.paymentId()));
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
