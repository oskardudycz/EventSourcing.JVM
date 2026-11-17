package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public sealed interface ShipmentExternalEvent {
  String referenceId();

  @Schema(description = "The warehouse set aside the products for the requester.")
  record StockReserved(
    ShipmentId shipmentId,
    String referenceId,
    OffsetDateTime reservedAt
  ) implements ShipmentExternalEvent {
  }

  @Schema(description = "The package left the warehouse. It carries the carrier and the tracking number.")
  record PackageWasSent(
    ShipmentId shipmentId,
    String referenceId,
    String carrier,
    String trackingNumber,
    OffsetDateTime sentAt
  ) implements ShipmentExternalEvent {
  }

  @Schema(description = "The products cannot ship. The reason tells if they are out of stock or the fulfillment service rejected the request.")
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
