package io.eventdriven.distributedprocesses.ecommerce.payments;

public record PaymentsModule(
  PaymentFacade facade,
  PaymentTimeoutWorker timeoutWorker,
  AuthorizationExpiryWorker authorizationExpiryWorker
) {
}
