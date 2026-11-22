package io.eventdriven.ecommerce.shipments;

import java.time.OffsetDateTime;

public sealed interface ShipmentEvent {
    record StockReserved(
            Shipment.Id shipmentId, String referenceId, Shipment.ProductItem[] productItems, OffsetDateTime reservedAt)
            implements ShipmentEvent {}

    record ProductWasOutOfStock(
            Shipment.Id shipmentId,
            String referenceId,
            Shipment.ProductItem[] productItems,
            OffsetDateTime availabilityCheckedAt)
            implements ShipmentEvent {}

    record FulfillmentRequested(Shipment.Id shipmentId, Shipment.ProductItem[] productItems, OffsetDateTime requestedAt)
            implements ShipmentEvent {}

    record FulfillmentRejected(Shipment.Id shipmentId, OffsetDateTime rejectedAt) implements ShipmentEvent {}

    record PackageWasSent(Shipment.Id shipmentId, String carrier, String trackingNumber, OffsetDateTime sentAt)
            implements ShipmentEvent {}

    record PackageWasDelivered(Shipment.Id shipmentId, OffsetDateTime deliveredAt) implements ShipmentEvent {}

    record StockReleased(Shipment.Id shipmentId, OffsetDateTime releasedAt) implements ShipmentEvent {}
}
