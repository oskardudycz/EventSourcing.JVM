package io.eventdriven.ecommerce.shipments;

import static io.eventdriven.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;

import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.ecommerce.shipments.external.ShipmentSummaryRepository;
import io.eventdriven.ecommerce.shipments.fulfillment.CallingBackFulfillmentService;
import io.eventdriven.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class ShipmentsConfigTests {
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Shipment.Id shipmentId = Shipment.Id.derivedFrom(referenceId);
    private final Shipment.ProductItem availableItem = new Shipment.ProductItem(UUID.randomUUID(), 2);
    private final Shipment.ProductItem[] available = new Shipment.ProductItem[] {availableItem};
    private final Function<Shipment.ProductItem, Boolean> isProductAvailable =
            item -> Set.of(availableItem).contains(item);
    private final OffsetDateTime now = OffsetDateTime.now();

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

    @Test
    public void theServiceSendingThePackageIsForwardedWithTheTracking() {
        var fulfillmentService = new CallingBackFulfillmentService(messageBus);
        configure(fulfillmentService);
        messageBus.publish(List.of(new ReserveStock(referenceId, available)));
        externalEvents.reset();

        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
        fulfillmentService.sendPackages();
        fulfillmentService.deliverPackages();

        externalEvents.shouldReceiveMessages(new ShipmentExternalEvent.PackageWasSent(
                shipmentId,
                referenceId,
                CallingBackFulfillmentService.carrier,
                CallingBackFulfillmentService.trackingNumber,
                now));
    }

    @Test
    public void theServiceRejectingTheRequestIsForwardedAsAShipmentFailure() {
        var fulfillmentService = new CallingBackFulfillmentService(messageBus);
        configure(fulfillmentService);
        messageBus.publish(List.of(new ReserveStock(referenceId, available)));
        externalEvents.reset();

        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
        fulfillmentService.rejectRequests();

        externalEvents.shouldReceiveMessages(new ShipmentExternalEvent.ShipmentFailed(
                shipmentId, referenceId, ShipmentExternalEvent.ShipmentFailed.Reason.FulfillmentRejected, now));
    }

    private void configure(FulfillmentService fulfillmentService) {
        messageBus.use(message -> {
            if (message instanceof ShipmentExternalEvent) externalEvents.catchMessage(message);
        });

        var shipmentsConfig = new ShipmentsConfig();
        shipmentsConfig.shipmentFacade(eventStore, TestTracing.untraced(), messageBus, isProductAvailable, () -> now);
        var shipmentSummaries = shipmentsConfig.shipmentSummaries(eventStore, H2.bean(ShipmentSummaryRepository.class));
        shipmentsConfig.shipmentExternalEventForwarder(eventStore, shipmentSummaries, messageBus);
        shipmentsConfig.fulfillmentServiceClient(eventStore, fulfillmentService);
    }
}
