package io.eventdriven.distributedprocesses.ecommerce.shipments;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;

import io.eventdriven.distributedprocesses.core.commands.CommandHandler;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;

public class ShipmentFacade {
    private final CommandHandler<ShipmentId, Shipment, ShipmentCommand, ShipmentEvent> commandHandler;

    public static String mapToStreamId(ShipmentId shipmentId) {
        return "Shipment-%s".formatted(shipmentId.tail());
    }

    public ShipmentFacade(CommandHandler<ShipmentId, Shipment, ShipmentCommand, ShipmentEvent> commandHandler) {
        this.commandHandler = commandHandler;
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.requests,
                            description = "Sets aside the requested products, or answers that they are out of stock."))
    public void reserveStock(ReserveStock command) {
        commandHandler.handle(ShipmentId.derivedFrom(command.referenceId()), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.requests,
                            description = "Asks the fulfillment service to send the products that are set aside."))
    public void requestFulfillment(RequestFulfillment command) {
        commandHandler.handle(command.shipmentId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.internal,
                            description = "Records that the package was sent."))
    public void confirmPackageSent(ConfirmPackageSent command) {
        commandHandler.handleContinuingTrace(command.shipmentId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.internal,
                            description = "Records that the fulfillment service will not send the package."))
    public void rejectFulfillment(RejectFulfillment command) {
        commandHandler.handleContinuingTrace(command.shipmentId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.internal,
                            description = "Records that the package was delivered."))
    public void deliverPackage(DeliverPackage command) {
        commandHandler.handleContinuingTrace(command.shipmentId(), command);
    }

    @AsyncListener(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.requests,
                            description = "Puts the products that are set aside back on sale."))
    public void releaseStock(ReleaseStock command) {
        commandHandler.handle(command.shipmentId(), command);
    }
}
