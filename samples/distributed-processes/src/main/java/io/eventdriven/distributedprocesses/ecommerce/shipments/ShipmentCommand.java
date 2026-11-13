package io.eventdriven.distributedprocesses.ecommerce.shipments;

public sealed interface ShipmentCommand {
  record ReserveStock(
    String referenceId,
    ProductItem[] productItems
  ) implements ShipmentCommand {
  }

  record RequestFulfillment(
    ShipmentId shipmentId
  ) implements ShipmentCommand {
  }

  record ConfirmPackageSent(
    ShipmentId shipmentId,
    String carrier,
    String trackingNumber
  ) implements ShipmentCommand {
  }

  record RejectFulfillment(
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
}
