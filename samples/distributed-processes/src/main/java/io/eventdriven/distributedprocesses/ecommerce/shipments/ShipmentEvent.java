package io.eventdriven.distributedprocesses.ecommerce.shipments;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface ShipmentEvent {
  record PackageWasSent(
    UUID packageId,
    UUID orderId,
    ProductItem productItems,
    OffsetDateTime sentAt) implements ShipmentEvent {
  }

  record ProductWasOutOfStock(
    UUID orderId,
    OffsetDateTime availabilityCheckedAt) implements ShipmentEvent {
  }
}
