package io.eventdriven.distributedprocesses.ecommerce.shipments.sandbox;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.ConfirmPackageSent;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentService;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

// Stands in for the warehouse when the application runs: it sends every package at once.
public class SandboxFulfillmentService implements FulfillmentService {
    public static final String carrier = "Sandbox Express";

    private final MessageBus messageBus;

    public SandboxFulfillmentService(MessageBus messageBus) {
        this.messageBus = messageBus;
    }

    @Override
    public void request(ShipmentId shipmentId, ProductItem[] productItems) {
        var trackingNumber = "SBX%010d".formatted(ThreadLocalRandom.current().nextLong(10_000_000_000L));

        messageBus.publish(List.of(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)));
    }
}
