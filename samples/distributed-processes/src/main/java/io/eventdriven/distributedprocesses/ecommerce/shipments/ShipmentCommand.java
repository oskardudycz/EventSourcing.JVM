package io.eventdriven.distributedprocesses.ecommerce.shipments;

import java.util.UUID;

public sealed interface ShipmentCommand {
  record DeliverPackage(UUID Id) implements ShipmentCommand {}

  record SendPackage(UUID OrderId, ProductItem[] ProductItems) implements ShipmentCommand {}
}
