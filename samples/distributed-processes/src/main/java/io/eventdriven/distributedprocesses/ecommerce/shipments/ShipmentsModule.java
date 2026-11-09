package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.eventdriven.distributedprocesses.ecommerce.shipments.reservation.ReservationExpiryWorker;


public record ShipmentsModule(
  ShipmentFacade facade,
  ReservationExpiryWorker reservationExpiryWorker
) {
}
