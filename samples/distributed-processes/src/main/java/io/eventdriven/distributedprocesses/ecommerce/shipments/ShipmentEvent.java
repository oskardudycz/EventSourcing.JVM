package io.eventdriven.distributedprocesses.ecommerce.shipments;

import java.time.OffsetDateTime;

public sealed interface ShipmentEvent {
  record StockReserved(
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    OffsetDateTime reservedAt,
    OffsetDateTime reservedUntil) implements ShipmentEvent {
  }

  record ProductWasOutOfStock(
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    OffsetDateTime availabilityCheckedAt) implements ShipmentEvent {
  }

  record ParcelBookingRequested(
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    OffsetDateTime requestedAt) implements ShipmentEvent {
  }

  record PackageWasSent(
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    String carrier,
    String trackingNumber,
    OffsetDateTime sentAt) implements ShipmentEvent {
  }

  record PackageWasDelivered(
    ShipmentId shipmentId,
    String referenceId,
    OffsetDateTime deliveredAt) implements ShipmentEvent {
  }

  record StockReleased(
    ShipmentId shipmentId,
    String referenceId,
    OffsetDateTime releasedAt) implements ShipmentEvent {
  }

  record StockReservationExpired(
    ShipmentId shipmentId,
    String referenceId,
    OffsetDateTime expiredAt) implements ShipmentEvent {
  }
}
