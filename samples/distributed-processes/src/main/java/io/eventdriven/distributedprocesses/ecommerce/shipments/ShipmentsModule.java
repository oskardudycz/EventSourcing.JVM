package io.eventdriven.distributedprocesses.ecommerce.shipments;

public record ShipmentsModule(
  ShipmentFacade facade,
  ReservationExpiryWorker reservationExpiryWorker
) {
}
