package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.AuthorizationExpiryWorker;
import io.eventdriven.distributedprocesses.ecommerce.payments.authorization.PaymentTimeoutWorker;


public record PaymentsModule(
  PaymentFacade facade,
  PaymentTimeoutWorker timeoutWorker,
  AuthorizationExpiryWorker authorizationExpiryWorker
) {
}
