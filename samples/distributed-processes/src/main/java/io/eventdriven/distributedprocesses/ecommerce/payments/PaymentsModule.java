package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledAuthorizationExpiryWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.ScheduledPaymentTimeoutWorker;


public record PaymentsModule(
  PaymentFacade facade,
  ScheduledPaymentTimeoutWorker timeoutWorker,
  ScheduledAuthorizationExpiryWorker authorizationExpiryWorker
) {
}
