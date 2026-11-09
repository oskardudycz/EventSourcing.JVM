package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.DeclineReason;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.DeclinePayment;
import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public class AuthorizationGatewayClient {
  private static final Logger logger = LoggerFactory.getLogger(AuthorizationGatewayClient.class);

  private final PaymentGateway paymentGateway;
  private final CommandBus commandBus;

  public AuthorizationGatewayClient(PaymentGateway paymentGateway, CommandBus commandBus) {
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

  // The module has already recorded the outcome, so a provider that refuses the call leaves
  // nothing to decide here: the money is settled between the two sides by hand.
  public void on(PaymentVoided event) {
    try {
      paymentGateway.voidAuthorization(event.paymentId());
    } catch (Exception failedCall) {
      logger.error("Voiding payment %s failed".formatted(event.paymentId()), failedCall);
    }
  }
}
