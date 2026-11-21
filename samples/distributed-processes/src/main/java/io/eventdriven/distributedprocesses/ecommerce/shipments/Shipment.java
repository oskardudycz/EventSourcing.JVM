package io.eventdriven.distributedprocesses.ecommerce.shipments;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;

public record Shipment(ShipmentId id, ProductItem[] productItems, Status status) {
    enum Status {
        Reserved,
        FulfillmentRequested,
        FulfillmentRejected,
        Sent,
        Delivered,
        Released,
        ProductsOutOfStock
    }

    public static Shipment initial() {
        return new Shipment(null, null, null);
    }

    boolean isStockHeld() {
        return status == Status.Reserved
                || status == Status.FulfillmentRequested
                || status == Status.FulfillmentRejected;
    }

    public static Shipment evolve(Shipment state, ShipmentEvent event) {
        return switch (event) {
            case StockReserved reserved ->
                new Shipment(reserved.shipmentId(), reserved.productItems(), Status.Reserved);
            case ProductWasOutOfStock outOfStock ->
                new Shipment(outOfStock.shipmentId(), state.productItems, Status.ProductsOutOfStock);
            case FulfillmentRequested ignored -> state.withStatus(Status.FulfillmentRequested);
            case FulfillmentRejected ignored -> state.withStatus(Status.FulfillmentRejected);
            case PackageWasSent ignored -> state.withStatus(Status.Sent);
            case PackageWasDelivered ignored -> state.withStatus(Status.Delivered);
            case StockReleased ignored -> state.withStatus(Status.Released);
        };
    }

    private Shipment withStatus(Status status) {
        return new Shipment(id, productItems, status);
    }
}
