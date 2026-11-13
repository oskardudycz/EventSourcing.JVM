package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

import java.time.OffsetDateTime;

public sealed interface ShipmentExternalEvent {
  record StockReserved(
    ShipmentId shipmentId,
    String referenceId,
    OffsetDateTime reservedAt
  ) implements ShipmentExternalEvent {
  }

  record PackageWasSent(
    ShipmentId shipmentId,
    String referenceId,
    String carrier,
    String trackingNumber,
    OffsetDateTime sentAt
  ) implements ShipmentExternalEvent {
  }

  record ShipmentFailed(
    ShipmentId shipmentId,
    String referenceId,
    Reason reason,
    OffsetDateTime failedAt
  ) implements ShipmentExternalEvent {
    public enum Reason {
      ProductsOutOfStock,
      FulfillmentRejected
    }
  }
}
