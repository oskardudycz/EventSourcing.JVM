package io.eventdriven.distributedprocesses.ecommerce.orders;

// Where the goods stood when the order gave up.
public enum OrderShipmentState {
  NotReserved,
  Reserved,
  Sent
}
