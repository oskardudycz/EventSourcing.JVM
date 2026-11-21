package io.eventdriven.distributedprocesses.ecommerce.shipments;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;

import io.eventdriven.distributedprocesses.core.eventstore.InMemoryEventStore;
import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.distributedprocesses.core.messaging.InMemoryMessageBus;
import io.eventdriven.distributedprocesses.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.CallingBackFulfillmentService;
import io.eventdriven.distributedprocesses.ecommerce.shipments.fulfillment.FulfillmentService;
import io.eventdriven.testing.MessageCatcher;
import io.eventdriven.testing.TestTracing;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class ShipmentsConfigTests {
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final ShipmentId shipmentId = ShipmentId.derivedFrom(referenceId);
    private final ProductItem availableItem = new ProductItem(UUID.randomUUID(), 2);
    private final ProductItem[] available = new ProductItem[] {availableItem};
    private final Function<ProductItem, Boolean> isProductAvailable =
            item -> Set.of(availableItem).contains(item);
    private final OffsetDateTime now = OffsetDateTime.now();

    private final InMemoryEventStore eventStore = new InMemoryEventStore(TestTracing.untraced());
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
        shipmentsConfig.shipmentFacade(eventStore, messageBus, isProductAvailable, () -> now);
        shipmentsConfig.shipmentExternalEventForwarder(eventStore, messageBus);
        shipmentsConfig.fulfillmentServiceClient(eventStore, fulfillmentService);
    }
}
