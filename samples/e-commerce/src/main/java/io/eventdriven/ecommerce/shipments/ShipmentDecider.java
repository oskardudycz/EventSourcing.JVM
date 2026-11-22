package io.eventdriven.ecommerce.shipments;

import static io.eventdriven.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.ecommerce.shipments.ShipmentEvent.*;

import io.eventdriven.ecommerce.shipments.Shipment.Status;

public final class ShipmentDecider {
    private static final ShipmentEvent[] noEvents = new ShipmentEvent[0];

    private ShipmentDecider() {}

    public static ShipmentEvent[] decide(ShipmentCommand command, Shipment state) {
        return switch (command) {
            case ReserveStock reserve -> reserveStock(reserve, state);
            case RequestFulfillment request -> requestFulfillment(request, state);
            case ConfirmPackageSent sent -> confirmSent(sent, state);
            case RejectFulfillment reject -> rejectFulfillment(reject, state);
            case DeliverPackage deliver -> deliver(deliver, state);
            case ReleaseStock release -> releaseStock(release, state);
        };
    }

    public static ShipmentEvent[] reserveStock(ReserveStock command, Shipment state) {
        if (state.status() != null) return noEvents;

        var shipmentId = Shipment.Id.derivedFrom(command.referenceId());
        var productItems = command.productItems();

        if (productItems.length == 0 || !command.productsAvailable())
            return new ShipmentEvent[] {
                new ProductWasOutOfStock(shipmentId, command.referenceId(), productItems, command.now())
            };

        return new ShipmentEvent[] {new StockReserved(shipmentId, command.referenceId(), productItems, command.now())};
    }

    public static ShipmentEvent[] requestFulfillment(RequestFulfillment command, Shipment state) {
        if (state.status() != Status.Reserved) return noEvents;

        return new ShipmentEvent[] {new FulfillmentRequested(state.id(), state.productItems(), command.now())};
    }

    public static ShipmentEvent[] confirmSent(ConfirmPackageSent command, Shipment state) {
        if (state.status() != Status.FulfillmentRequested) return noEvents;

        return new ShipmentEvent[] {
            new PackageWasSent(state.id(), command.carrier(), command.trackingNumber(), command.now())
        };
    }

    public static ShipmentEvent[] rejectFulfillment(RejectFulfillment command, Shipment state) {
        if (state.status() != Status.FulfillmentRequested) return noEvents;

        return new ShipmentEvent[] {new FulfillmentRejected(state.id(), command.now())};
    }

    public static ShipmentEvent[] deliver(DeliverPackage command, Shipment state) {
        if (state.status() != Status.Sent) return noEvents;

        return new ShipmentEvent[] {new PackageWasDelivered(state.id(), command.now())};
    }

    public static ShipmentEvent[] releaseStock(ReleaseStock command, Shipment state) {
        if (!state.isStockHeld()) return noEvents;

        return new ShipmentEvent[] {new StockReleased(state.id(), command.now())};
    }
}
