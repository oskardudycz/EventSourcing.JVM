package io.eventdriven.ecommerce.shipments;

import static io.eventdriven.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.ecommerce.shipments.ShipmentDecider.*;

import io.eventdriven.core.commands.CommandHandler;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

public class ShipmentFacade {
    private final CommandHandler<Shipment.Id, Shipment, ShipmentEvent> commandHandler;
    private final Function<Shipment.ProductItem, Boolean> isProductAvailable;
    private final Supplier<OffsetDateTime> now;

    public static String mapToStreamId(Shipment.Id shipmentId) {
        return "Shipment-%s".formatted(shipmentId.tail());
    }

    public ShipmentFacade(
            CommandHandler<Shipment.Id, Shipment, ShipmentEvent> commandHandler,
            Function<Shipment.ProductItem, Boolean> isProductAvailable,
            Supplier<OffsetDateTime> now) {
        this.commandHandler = commandHandler;
        this.isProductAvailable = isProductAvailable;
        this.now = now;
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.requests,
                            description = "Sets aside the requested products, or answers that they are out of stock."))
    public void reserveStock(ReserveStock command) {
        var decided = new ReserveStock(
                command.referenceId(),
                command.productItems(),
                Arrays.stream(command.productItems()).allMatch(isProductAvailable::apply),
                now.get());

        commandHandler.handle(
                Shipment.Id.derivedFrom(command.referenceId()), state -> ShipmentDecider.reserveStock(decided, state));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.requests,
                            description = "Asks the fulfillment service to send the products that are set aside."))
    public void requestFulfillment(RequestFulfillment command) {
        var decided = new RequestFulfillment(command.shipmentId(), now.get());

        commandHandler.handle(command.shipmentId(), state -> ShipmentDecider.requestFulfillment(decided, state));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.internal,
                            description = "Records that the package was sent."))
    public void confirmPackageSent(ConfirmPackageSent command) {
        var decided =
                new ConfirmPackageSent(command.shipmentId(), command.carrier(), command.trackingNumber(), now.get());

        commandHandler.handleContinuingTrace(
                command.shipmentId(), ConfirmPackageSent.class.getSimpleName(), state -> confirmSent(decided, state));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.internal,
                            description = "Records that the fulfillment service will not send the package."))
    public void rejectFulfillment(RejectFulfillment command) {
        var decided = new RejectFulfillment(command.shipmentId(), now.get());

        commandHandler.handleContinuingTrace(
                command.shipmentId(),
                RejectFulfillment.class.getSimpleName(),
                state -> ShipmentDecider.rejectFulfillment(decided, state));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.internal,
                            description = "Records that the package was delivered."))
    public void deliverPackage(DeliverPackage command) {
        var decided = new DeliverPackage(command.shipmentId(), now.get());

        commandHandler.handleContinuingTrace(
                command.shipmentId(), DeliverPackage.class.getSimpleName(), state -> deliver(decided, state));
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.requests,
                            description = "Puts the products that are set aside back on sale."))
    public void releaseStock(ReleaseStock command) {
        var decided = new ReleaseStock(command.shipmentId(), now.get());

        commandHandler.handle(command.shipmentId(), state -> ShipmentDecider.releaseStock(decided, state));
    }
}
