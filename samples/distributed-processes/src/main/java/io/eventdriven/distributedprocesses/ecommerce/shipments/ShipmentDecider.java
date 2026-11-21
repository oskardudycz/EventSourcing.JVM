package io.eventdriven.distributedprocesses.ecommerce.shipments;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;

import io.eventdriven.distributedprocesses.ecommerce.shipments.Shipment.Status;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

public class ShipmentDecider {
    private static final ShipmentEvent[] noEvents = new ShipmentEvent[0];

    private final Function<ProductItem, Boolean> isProductAvailable;
    private final Supplier<OffsetDateTime> now;

    public ShipmentDecider(Function<ProductItem, Boolean> isProductAvailable, Supplier<OffsetDateTime> now) {
        this.isProductAvailable = isProductAvailable;
        this.now = now;
    }

    public ShipmentEvent[] handle(ShipmentCommand command, Shipment state) {
        return switch (command) {
            case ReserveStock reserve -> reserveStock(reserve, state);
            case RequestFulfillment ignored -> requestFulfillment(state);
            case ConfirmPackageSent sent -> confirmSent(sent, state);
            case RejectFulfillment ignored -> rejectFulfillment(state);
            case DeliverPackage ignored -> deliver(state);
            case ReleaseStock ignored -> releaseStock(state);
        };
    }

    private ShipmentEvent[] reserveStock(ReserveStock command, Shipment state) {
        if (state.status() != null) return noEvents;

        var shipmentId = ShipmentId.derivedFrom(command.referenceId());
        var productItems = command.productItems();

        // allMatch on an empty stream is true
        if (productItems.length == 0 || !Arrays.stream(productItems).allMatch(isProductAvailable::apply))
            return new ShipmentEvent[] {
                new ProductWasOutOfStock(shipmentId, command.referenceId(), productItems, now.get())
            };

        return new ShipmentEvent[] {new StockReserved(shipmentId, command.referenceId(), productItems, now.get())};
    }

    private ShipmentEvent[] requestFulfillment(Shipment state) {
        if (state.status() != Status.Reserved) return noEvents;

        return new ShipmentEvent[] {new FulfillmentRequested(state.id(), state.productItems(), now.get())};
    }

    private ShipmentEvent[] confirmSent(ConfirmPackageSent command, Shipment state) {
        if (state.status() != Status.FulfillmentRequested) return noEvents;

        return new ShipmentEvent[] {
            new PackageWasSent(state.id(), command.carrier(), command.trackingNumber(), now.get())
        };
    }

    private ShipmentEvent[] rejectFulfillment(Shipment state) {
        if (state.status() != Status.FulfillmentRequested) return noEvents;

        return new ShipmentEvent[] {new FulfillmentRejected(state.id(), now.get())};
    }

    private ShipmentEvent[] deliver(Shipment state) {
        if (state.status() != Status.Sent) return noEvents;

        return new ShipmentEvent[] {new PackageWasDelivered(state.id(), now.get())};
    }

    private ShipmentEvent[] releaseStock(Shipment state) {
        if (!state.isStockHeld()) return noEvents;

        return new ShipmentEvent[] {new StockReleased(state.id(), now.get())};
    }
}
