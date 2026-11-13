package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;

import java.time.OffsetDateTime;
import java.util.List;


import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.ExpirePaymentAuthorization;

public class AuthorizationExpiryWorker {
  private final AuthorizedPayments authorizedPayments;
  private final MessageBus messageBus;

  public AuthorizationExpiryWorker(
    AuthorizedPayments authorizedPayments,
    MessageBus messageBus
  ) {
    this.authorizedPayments = authorizedPayments;
    this.messageBus = messageBus;
  }

  public void handle(OffsetDateTime now) {
    for (var paymentId : authorizedPayments.expiredAt(now)) {
      messageBus.publish(List.of(new ExpirePaymentAuthorization(paymentId, now)));
    }
  }
}
