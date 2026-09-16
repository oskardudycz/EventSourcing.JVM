package io.eventdriven.distributedprocesses.ecommerce.shipments;

import java.time.OffsetDateTime;

public sealed interface ShipmentCommand {
  record ReserveStock(
    String referenceId,
    ProductItem[] productItems
  ) implements ShipmentCommand {
  }

  record SendPackage(
    ShipmentId shipmentId
  ) implements ShipmentCommand {
  }

  record DeliverPackage(
    ShipmentId shipmentId
  ) implements ShipmentCommand {
  }

  record ReleaseStock(
    ShipmentId shipmentId
  ) implements ShipmentCommand {
  }

  record ExpireStockReservation(
    ShipmentId shipmentId,
    OffsetDateTime expiredAt
  ) implements ShipmentCommand {
  }
}
