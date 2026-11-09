package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;

import java.time.OffsetDateTime;

public sealed interface ShipmentExternalEvent {
  record StockReserved(
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    OffsetDateTime reservedAt,
    OffsetDateTime reservedUntil
  ) implements ShipmentExternalEvent {
  }

  record PackageWasSent(
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    String carrier,
    String trackingNumber,
    OffsetDateTime sentAt
  ) implements ShipmentExternalEvent {
  }

  record PackageWasDelivered(
    ShipmentId shipmentId,
    String referenceId,
    OffsetDateTime deliveredAt
  ) implements ShipmentExternalEvent {
  }

  record StockReservationFailed(
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    Reason reason,
    OffsetDateTime failedAt
  ) implements ShipmentExternalEvent {
    public enum Reason {
      ProductsOutOfStock,
      ReservationExpired
    }
  }
}
