package io.eventdriven.distributedprocesses.ecommerce.orders;

// Where the money stood when the order gave up. It is the order, not the policy, that knows
// whether a hold must be dropped or nothing done at all.
public enum OrderPaymentState {
  NotAuthorized,
  Authorized,
  Captured
}
