package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;

import java.time.OffsetDateTime;

public sealed interface ShipmentExternalEvent {
  record StockReserved(
    ShipmentId shipmentId,
    String referenceId,
    OffsetDateTime reservedAt,
    OffsetDateTime reservedUntil
  ) implements ShipmentExternalEvent {
  }

  record ProductWasOutOfStock(
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    OffsetDateTime availabilityCheckedAt
  ) implements ShipmentExternalEvent {
  }

  record PackageWasSent(
    ShipmentId shipmentId,
    String referenceId,
    ProductItem[] productItems,
    OffsetDateTime sentAt
  ) implements ShipmentExternalEvent {
  }

  record PackageWasDelivered(
    ShipmentId shipmentId,
    String referenceId,
    OffsetDateTime deliveredAt
  ) implements ShipmentExternalEvent {
  }

  record StockReservationExpired(
    ShipmentId shipmentId,
    String referenceId,
    OffsetDateTime expiredAt
  ) implements ShipmentExternalEvent {
  }
}
