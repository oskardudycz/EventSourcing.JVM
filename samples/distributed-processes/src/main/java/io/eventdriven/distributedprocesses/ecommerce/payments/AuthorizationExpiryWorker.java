package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;

import java.time.OffsetDateTime;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ExpirePaymentAuthorization;

public class AuthorizationExpiryWorker {
  private final AuthorizedPayments authorizedPayments;
  private final CommandBus commandBus;

  public AuthorizationExpiryWorker(
    AuthorizedPayments authorizedPayments,
    CommandBus commandBus
  ) {
    this.authorizedPayments = authorizedPayments;
    this.commandBus = commandBus;
  }

  // Production would call this on a schedule; tests call it directly to keep time explicit.
  public void run(OffsetDateTime now) {
    for (var paymentId : authorizedPayments.expiredAt(now)) {
      commandBus.send(new ExpirePaymentAuthorization(paymentId, now));
    }
  }
}
