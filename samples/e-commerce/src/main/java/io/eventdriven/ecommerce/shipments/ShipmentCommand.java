package io.eventdriven.ecommerce.shipments;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

public sealed interface ShipmentCommand {
    @Schema(description = "Asks the warehouse to set aside the products, so that nobody else can buy them.")
    record ReserveStock(
            String referenceId,
            Shipment.ProductItem[] productItems,
            @Nullable Boolean productsAvailable,
            @Nullable OffsetDateTime now)
            implements ShipmentCommand {
        public ReserveStock(String referenceId, Shipment.ProductItem[] productItems) {
            this(referenceId, productItems, null, null);
        }
    }

    @Schema(description = "Asks the fulfillment service to pick, pack and send the products that are set aside.")
    record RequestFulfillment(
            Shipment.Id shipmentId, @Nullable OffsetDateTime now) implements ShipmentCommand {
        public RequestFulfillment(Shipment.Id shipmentId) {
            this(shipmentId, null);
        }
    }

    @Schema(
            description =
                    "The fulfillment service's answer: the package was sent, with this carrier and tracking number.")
    record ConfirmPackageSent(
            Shipment.Id shipmentId,
            String carrier,
            String trackingNumber,
            @Nullable OffsetDateTime now) implements ShipmentCommand {
        public ConfirmPackageSent(Shipment.Id shipmentId, String carrier, String trackingNumber) {
            this(shipmentId, carrier, trackingNumber, null);
        }
    }

    @Schema(description = "The fulfillment service's answer: it will not send the package.")
    record RejectFulfillment(
            Shipment.Id shipmentId, @Nullable OffsetDateTime now) implements ShipmentCommand {
        public RejectFulfillment(Shipment.Id shipmentId) {
            this(shipmentId, null);
        }
    }

    @Schema(description = "Records that the carrier delivered the package.")
    record DeliverPackage(Shipment.Id shipmentId, @Nullable OffsetDateTime now) implements ShipmentCommand {
        public DeliverPackage(Shipment.Id shipmentId) {
            this(shipmentId, null);
        }
    }

    @Schema(description = "Puts the products that are set aside back on sale.")
    record ReleaseStock(Shipment.Id shipmentId, @Nullable OffsetDateTime now) implements ShipmentCommand {
        public ReleaseStock(Shipment.Id shipmentId) {
            this(shipmentId, null);
        }
    }
}
