package io.eventdriven.ecommerce.shipments.external;

import static io.eventdriven.ecommerce.shipments.ShipmentEvent.*;
import static io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent.*;

import io.eventdriven.core.messaging.MessageBus;
import io.eventdriven.core.messaging.MessageEnvelope;
import io.eventdriven.core.processing.Forwarder;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.ShipmentEvent;
import io.eventdriven.ecommerce.shipments.ShipmentsConfig;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import java.util.List;

public class ShipmentExternalEventForwarder implements Forwarder {
    private final ShipmentSummaries summaries;
    private final MessageBus messageBus;

    public ShipmentExternalEventForwarder(ShipmentSummaries summaries, MessageBus messageBus) {
        this.summaries = summaries;
        this.messageBus = messageBus;
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(
                ShipmentEvent.StockReserved.class,
                ProductWasOutOfStock.class,
                ShipmentEvent.PackageWasSent.class,
                FulfillmentRejected.class);
    }

    @Override
    public void handle(MessageEnvelope<Object> envelope) {
        var metadata = envelope.metadata();

        switch (envelope.message()) {
            case ShipmentEvent.StockReserved event -> onStockReserved(new MessageEnvelope<>(event, metadata));
            case ProductWasOutOfStock event -> onProductWasOutOfStock(new MessageEnvelope<>(event, metadata));
            case ShipmentEvent.PackageWasSent event -> onPackageWasSent(new MessageEnvelope<>(event, metadata));
            case FulfillmentRejected event -> onFulfillmentRejected(new MessageEnvelope<>(event, metadata));
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(envelope.message()));
        }
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.responses,
                            payloadType = ShipmentExternalEvent.StockReserved.class,
                            description = "Tells the requester that its products are set aside."))
    public void onStockReserved(MessageEnvelope<ShipmentEvent.StockReserved> envelope) {
        var event = envelope.message();
        messageBus.publish(List.of(
                new ShipmentExternalEvent.StockReserved(event.shipmentId(), event.referenceId(), event.reservedAt())));
    }

    @AsyncPublisher(
            operation =
                    @AsyncOperation(
                            channelName = ShipmentsConfig.responses,
                            payloadType = ShipmentExternalEvent.PackageWasSent.class,
                            description = "Tells the requester that its package was sent."))
    public void onPackageWasSent(MessageEnvelope<ShipmentEvent.PackageWasSent> envelope) {
        var event = envelope.message();
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
    public void onProductWasOutOfStock(MessageEnvelope<ProductWasOutOfStock> envelope) {
        var event = envelope.message();
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
    public void onFulfillmentRejected(MessageEnvelope<ShipmentEvent.FulfillmentRejected> envelope) {
        var event = envelope.message();
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
