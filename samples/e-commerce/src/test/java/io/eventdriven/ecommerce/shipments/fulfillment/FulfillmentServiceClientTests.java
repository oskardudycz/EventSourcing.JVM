package io.eventdriven.ecommerce.shipments.fulfillment;

import static io.eventdriven.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.ecommerce.shipments.ShipmentEvent.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.shipments.Shipment;
import io.eventdriven.ecommerce.shipments.ShipmentsConfig;
import io.eventdriven.ecommerce.shipments.external.ShipmentSummaryRepository;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class FulfillmentServiceClientTests {

    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Shipment.Id shipmentId = Shipment.Id.derivedFrom(referenceId);
    private final Shipment.ProductItem availableItem = new Shipment.ProductItem(UUID.randomUUID(), 2);
    private final Shipment.ProductItem unavailableItem = new Shipment.ProductItem(UUID.randomUUID(), 1);
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
    private final MessageCatcher published = new MessageCatcher();

    @Test
    public void callingBackServiceSendsThenDeliversThePackage() {
        var fulfillmentService = new CallingBackFulfillmentService(messageBus);
        configure(fulfillmentService);

        messageBus.publish(List.of(new ReserveStock(referenceId, available)));
        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
        published.reset();

        fulfillmentService.sendPackages();
        fulfillmentService.deliverPackages();

        published.shouldReceiveMessages(
                new PackageWasSent(
                        shipmentId,
                        CallingBackFulfillmentService.carrier,
                        CallingBackFulfillmentService.trackingNumber,
                        now),
                new PackageWasDelivered(shipmentId, now));
    }

    @Test
    public void silentServiceLeavesThePackageWaitingForFulfillment() {
        configure(new SilentFulfillmentService());

        messageBus.publish(List.of(new ReserveStock(referenceId, available)));
        published.reset();

        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));

        published.shouldReceiveMessages(new FulfillmentRequested(shipmentId, available, now));
    }

    @Test
    public void unreachableServiceLeavesTheStockHeldInsteadOfThrowing() {
        configure(new ThrowingFulfillmentService());

        messageBus.publish(List.of(new ReserveStock(referenceId, available)));
        published.reset();

        assertThatCode(() -> messageBus.publish(List.of(new RequestFulfillment(shipmentId))))
                .doesNotThrowAnyException();

        messageBus.publish(List.of(new ReleaseStock(shipmentId)));

        published.shouldReceiveMessages(
                new FulfillmentRequested(shipmentId, available, now), new StockReleased(shipmentId, now));
    }

    @Test
    public void serviceIsAskedWithTheShipmentAndItsReservedItems() {
        var requests = new ArrayList<String>();
        configure((requestedShipmentId, productItems) ->
                requests.add("%s:%s".formatted(requestedShipmentId, productItems.length)));

        messageBus.publish(List.of(new ReserveStock(referenceId, available)));
        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));

        assertThat(requests).containsExactly("%s:%s".formatted(shipmentId, available.length));
    }

    @Test
    public void requestingFulfillmentTwiceAsksTheServiceOnce() {
        var asked = new ArrayList<Shipment.Id>();
        configure((requestedShipmentId, productItems) -> asked.add(requestedShipmentId));

        messageBus.publish(List.of(new ReserveStock(referenceId, available)));
        messageBus.publish(List.of(new RequestFulfillment(shipmentId), new RequestFulfillment(shipmentId)));

        assertThat(asked).containsExactly(shipmentId);
    }

    @Test
    public void aReservationIsNeverSentToTheServiceBeforeFulfillmentIsRequested() {
        var asked = new ArrayList<Shipment.Id>();
        configure((requestedShipmentId, productItems) -> asked.add(requestedShipmentId));

        messageBus.publish(List.of(new ReserveStock(referenceId, available)));

        assertThat(asked).isEmpty();
    }

    @Test
    public void outOfStockShipmentIsNeverSentToTheService() {
        var asked = new ArrayList<Shipment.Id>();
        configure((requestedShipmentId, productItems) -> asked.add(requestedShipmentId));

        messageBus.publish(List.of(new ReserveStock(referenceId, new Shipment.ProductItem[] {unavailableItem})));
        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));

        assertThat(asked).isEmpty();
    }

    @Test
    public void callingBackServiceCanRejectTheRequest() {
        var fulfillmentService = new CallingBackFulfillmentService(messageBus);
        configure(fulfillmentService);

        messageBus.publish(List.of(new ReserveStock(referenceId, available)));
        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
        published.reset();

        fulfillmentService.rejectRequests();

        published.shouldReceiveMessages(new FulfillmentRejected(shipmentId, now));
    }

    private void configure(FulfillmentService fulfillmentService) {
        eventStore.use(published::catchMessage);

        var shipmentsConfig = new ShipmentsConfig();
        shipmentsConfig.shipmentFacade(eventStore, TestTracing.untraced(), messageBus, isProductAvailable, () -> now);
        var shipmentSummaries = shipmentsConfig.shipmentSummaries(eventStore, H2.bean(ShipmentSummaryRepository.class));
        shipmentsConfig.shipmentExternalEventForwarder(eventStore, shipmentSummaries, messageBus);
        shipmentsConfig.fulfillmentServiceClient(eventStore, fulfillmentService);
    }
}
