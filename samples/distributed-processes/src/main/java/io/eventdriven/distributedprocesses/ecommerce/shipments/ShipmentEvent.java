package io.eventdriven.distributedprocesses.ecommerce.shipments;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface ShipmentEvent {
  record PackageWasSent(
    UUID shipmentId,
    UUID orderId,
    ProductItem[] productItems,
    OffsetDateTime sentAt) implements ShipmentEvent {
  }

  record ProductWasOutOfStock(
    UUID shipmentId,
    UUID orderId,
    ProductItem[] productItems,
    OffsetDateTime availabilityCheckedAt) implements ShipmentEvent {
  }
}
