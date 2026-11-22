package io.eventdriven.ecommerce.shipments.external;

import static io.eventdriven.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.ecommerce.shipments.ShipmentEvent.StockReserved;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;

import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.ShipmentFacade;
import io.eventdriven.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.ecommerce.shipments.fulfillment.SilentFulfillmentService;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.messaging.Metadata;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ShipmentExternalEventForwarderOrderingTests {
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Shipment.Id shipmentId = Shipment.Id.derivedFrom(referenceId);
    private final Shipment.ProductItem[] available =
            new Shipment.ProductItem[] {new Shipment.ProductItem(UUID.randomUUID(), 2)};
    private final OffsetDateTime now = OffsetDateTime.now();
    private final String carrier = "DHL";
    private final String trackingNumber = "JD014600006281234567";

    private final InMemoryEventStore eventStore =
            new InMemoryEventStore(TestTracing.untraced(), ECommerceMessages.serializer());
    private final InMemoryMessageBus messageBus = new InMemoryMessageBus(
            TestTracing.untraced(),
            List.of(ShipmentsConfig.shipmentsRoutes()),
            List.of(
                    ShipmentsConfig.shipmentsRequestsChannel(),
                    ShipmentsConfig.shipmentsResponsesChannel(),
                    ShipmentsConfig.shipmentsInternalChannel()),
            List.of(ShipmentsConfig.shipmentsRequestsQueue(), ShipmentsConfig.shipmentsInternalQueue()));
    private final MessageCatcher externalEvents = new MessageCatcher();
    private final ShipmentSummaries summaries = new ShipmentSummaries(H2.bean(ShipmentSummaryRepository.class));

    public ShipmentExternalEventForwarderOrderingTests() {
        messageBus.use(message -> {
            if (message instanceof ShipmentExternalEvent) externalEvents.catchMessage(message);
        });

        var shipmentsConfig = new ShipmentsConfig();
        shipmentsConfig.shipmentFacade(eventStore, TestTracing.untraced(), messageBus, item -> true, () -> now);
        shipmentsConfig.shipmentExternalEventForwarder(eventStore, summaries, messageBus);
        shipmentsConfig.fulfillmentServiceClient(eventStore, new SilentFulfillmentService());
    }

    @Test
    public void theForwarderWaitsUntilTheSummaryHasCaughtUp() {
        messageBus.publish(List.of(new ReserveStock(referenceId, available)));
        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
        externalEvents.reset();

        messageBus.publish(List.of(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)));

        externalEvents.shouldNotReceiveAnyEvent();

        summaries.handle(new EventEnvelope<>(
                new StockReserved(shipmentId, referenceId, available, now),
                Metadata.at(ShipmentFacade.mapToStreamId(shipmentId), 0)));
        messageBus.publish(List.of(new DeliverPackage(shipmentId)));

        externalEvents.shouldReceiveMessages(
                new ShipmentExternalEvent.PackageWasSent(shipmentId, referenceId, carrier, trackingNumber, now));
    }

    @Test
    public void theForwarderWaitsUntilTheSummaryHasCaughtUpBeforeForwardingARejection() {
        messageBus.publish(List.of(new ReserveStock(referenceId, available)));
        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
        externalEvents.reset();

        messageBus.publish(List.of(new RejectFulfillment(shipmentId)));

        externalEvents.shouldNotReceiveAnyEvent();

        summaries.handle(new EventEnvelope<>(
                new StockReserved(shipmentId, referenceId, available, now),
                Metadata.at(ShipmentFacade.mapToStreamId(shipmentId), 0)));
        messageBus.publish(List.of(new ReleaseStock(shipmentId)));

        externalEvents.shouldReceiveMessages(new ShipmentExternalEvent.ShipmentFailed(
                shipmentId, referenceId, ShipmentExternalEvent.ShipmentFailed.Reason.FulfillmentRejected, now));
    }
}
