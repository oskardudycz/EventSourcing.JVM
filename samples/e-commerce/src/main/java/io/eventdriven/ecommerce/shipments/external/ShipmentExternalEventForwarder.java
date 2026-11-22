package io.eventdriven.ecommerce.shipments.external;

import static io.eventdriven.ecommerce.shipments.ShipmentEvent.*;
import static io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent.*;

import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.ShipmentEvent;
import io.eventdriven.ecommerce.shipments.ShipmentsConfig;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import java.util.List;

public class ShipmentExternalEventForwarder {
    private final ShipmentSummaries summaries;
    private final MessageBus messageBus;

    public ShipmentExternalEventForwarder(ShipmentSummaries summaries, MessageBus messageBus) {
        this.summaries = summaries;
        this.messageBus = messageBus;
    }

    public void handle(EventEnvelope<Object> envelope) {
        var metadata = envelope.metadata();

        switch (envelope.data()) {
            case ShipmentEvent.StockReserved event -> onStockReserved(new EventEnvelope<>(event, metadata));
            case ProductWasOutOfStock event -> onProductWasOutOfStock(new EventEnvelope<>(event, metadata));
            case ShipmentEvent.PackageWasSent event -> onPackageWasSent(new EventEnvelope<>(event, metadata));
            case FulfillmentRejected event -> onFulfillmentRejected(new EventEnvelope<>(event, metadata));
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(envelope.data()));
        }
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.responses,
                            payloadType = ShipmentExternalEvent.StockReserved.class,
                            description = "Tells the requester that its products are set aside."))
    public void onStockReserved(EventEnvelope<ShipmentEvent.StockReserved> envelope) {
        var event = envelope.data();
        messageBus.publish(List.of(
                new ShipmentExternalEvent.StockReserved(event.shipmentId(), event.referenceId(), event.reservedAt())));
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.responses,
                            payloadType = ShipmentExternalEvent.PackageWasSent.class,
                            description = "Tells the requester that its package was sent."))
    public void onPackageWasSent(EventEnvelope<ShipmentEvent.PackageWasSent> envelope) {
        var event = envelope.data();
        messageBus.publish(List.of(new ShipmentExternalEvent.PackageWasSent(
                event.shipmentId(),
                referenceIdOf(event.shipmentId()),
                event.carrier(),
                event.trackingNumber(),
                event.sentAt())));
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.responses,
                            payloadType = ShipmentExternalEvent.ShipmentFailed.class,
                            description = "Tells the requester that its products cannot ship."))
    public void onProductWasOutOfStock(EventEnvelope<ProductWasOutOfStock> envelope) {
        var event = envelope.data();
        messageBus.publish(List.of(new ShipmentFailed(
                event.shipmentId(),
                event.referenceId(),
                ShipmentFailed.Reason.ProductsOutOfStock,
                event.availabilityCheckedAt())));
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.responses,
                            payloadType = ShipmentExternalEvent.ShipmentFailed.class,
                            description = "Tells the requester that its products cannot ship."))
    public void onFulfillmentRejected(EventEnvelope<ShipmentEvent.FulfillmentRejected> envelope) {
        var event = envelope.data();
        messageBus.publish(List.of(new ShipmentFailed(
                event.shipmentId(),
                referenceIdOf(event.shipmentId()),
                ShipmentFailed.Reason.FulfillmentRejected,
                event.rejectedAt())));
    }

    private String referenceIdOf(Shipment.Id shipmentId) {
        return summaries
                .get(shipmentId)
                .orElseThrow(() ->
                        new IllegalStateException("Shipment '%s' has no summary yet".formatted(shipmentId.value())))
                .getReferenceId();
    }
}
