package io.eventdriven.distributedprocesses.ecommerce.shipments;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import java.time.OffsetDateTime;
import java.util.function.Function;
import java.util.function.Supplier;

public class ShipmentFacade {
    private final AggregateStore<Shipment, ShipmentEvent, ShipmentId> store;
    private final Function<ProductItem, Boolean> isProductAvailable;
    private final Supplier<OffsetDateTime> now;

    public static String mapToStreamId(ShipmentId shipmentId) {
        return "Shipment-%s".formatted(shipmentId.tail());
    }

    public ShipmentFacade(
            AggregateStore<Shipment, ShipmentEvent, ShipmentId> store,
            Function<ProductItem, Boolean> isProductAvailable,
            Supplier<OffsetDateTime> now) {
        this.store = store;
        this.isProductAvailable = isProductAvailable;
        this.now = now;
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.requests,
                            description = "Sets aside the requested products, or answers that they are out of stock."))
    public void reserveStock(ReserveStock command) {
        var shipmentId = ShipmentId.derivedFrom(command.referenceId());
        store.getAndUpdate(
                shipmentId,
                current -> current.reserveStock(
                        isProductAvailable, shipmentId, command.referenceId(), command.productItems(), now.get()));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.requests,
                            description = "Asks the fulfillment service to send the products that are set aside."))
    public void requestFulfillment(RequestFulfillment command) {
        store.getAndUpdate(command.shipmentId(), current -> current.requestFulfillment(now.get()));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.internal,
                            description = "Records that the package was sent."))
    public void confirmPackageSent(ConfirmPackageSent command) {
        store.getAndUpdateContinuingTrace(
                command.shipmentId(),
                command,
                current -> current.confirmSent(command.carrier(), command.trackingNumber(), now.get()));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.internal,
                            description = "Records that the fulfillment service will not send the package."))
    public void rejectFulfillment(RejectFulfillment command) {
        store.getAndUpdateContinuingTrace(
                command.shipmentId(), command, current -> current.rejectFulfillment(now.get()));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.internal,
                            description = "Records that the package was delivered."))
    public void deliverPackage(DeliverPackage command) {
        store.getAndUpdateContinuingTrace(command.shipmentId(), command, current -> current.deliver(now.get()));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.requests,
                            description = "Puts the products that are set aside back on sale."))
    public void releaseStock(ReleaseStock command) {
        store.getAndUpdate(command.shipmentId(), current -> current.releaseStock(now.get()));
    }
}
