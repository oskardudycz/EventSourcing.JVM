package io.eventdriven.distributedprocesses.ecommerce.shipments;

import io.swagger.v3.oas.annotations.media.Schema;

public sealed interface ShipmentCommand {
    @Schema(description = "Asks the warehouse to set aside the products, so that nobody else can buy them.")
    record ReserveStock(String referenceId, ProductItem[] productItems) implements ShipmentCommand {}

    @Schema(description = "Asks the fulfillment service to pick, pack and send the products that are set aside.")
    record RequestFulfillment(ShipmentId shipmentId) implements ShipmentCommand {}

    @Schema(
            description =
                    "The fulfillment service's answer: the package was sent, with this carrier and tracking number.")
    record ConfirmPackageSent(ShipmentId shipmentId, String carrier, String trackingNumber)
            implements ShipmentCommand {}

    @Schema(description = "The fulfillment service's answer: it will not send the package.")
    record RejectFulfillment(ShipmentId shipmentId) implements ShipmentCommand {}

    @Schema(description = "Records that the carrier delivered the package.")
    record DeliverPackage(ShipmentId shipmentId) implements ShipmentCommand {}

    @Schema(description = "Puts the products that are set aside back on sale.")
    record ReleaseStock(ShipmentId shipmentId) implements ShipmentCommand {}
}
