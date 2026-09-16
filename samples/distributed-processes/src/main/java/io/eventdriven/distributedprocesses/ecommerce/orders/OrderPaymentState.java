package io.eventdriven.distributedprocesses.ecommerce.orders;

// Where the money stood when the order gave up. It is the order, not the saga, that knows
// whether a hold must be dropped, a charge given back, or nothing done at all.
public enum OrderPaymentState {
  NotAuthorized,
  Authorized,
  Captured
}
