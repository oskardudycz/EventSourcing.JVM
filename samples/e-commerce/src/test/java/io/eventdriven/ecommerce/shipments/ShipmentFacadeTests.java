package io.eventdriven.ecommerce.shipments;

import static io.eventdriven.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.ecommerce.shipments.ShipmentEvent.*;
import static io.eventdriven.testing.projections.ProjectionsDatabase.H2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.eventdriven.core.eventstore.EventStore;
import io.eventdriven.core.eventstore.inmemory.InMemoryEventStore;
import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.core.messaging.EventEnvelope;
import io.eventdriven.core.messaging.inmemory.InMemoryMessageBus;
import io.eventdriven.ecommerce.ECommerceMessages;
import io.eventdriven.ecommerce.shipments.external.ShipmentSummaryRepository;
import io.eventdriven.ecommerce.shipments.fulfillment.SilentFulfillmentService;
import io.eventdriven.testing.messaging.MessageCatcher;
import io.eventdriven.testing.tracing.TestTracing;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class ShipmentFacadeTests {

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
    private final String carrier = "DHL";
    private final String trackingNumber = "JD014600006281234567";

    public ShipmentFacadeTests() {
        eventStore.use(published::catchMessage);
        var shipmentsConfig = new ShipmentsConfig();
        shipmentsConfig.shipmentFacade(eventStore, TestTracing.untraced(), messageBus, isProductAvailable, () -> now);
        var shipmentSummaries = shipmentsConfig.shipmentSummaries(eventStore, H2.bean(ShipmentSummaryRepository.class));
        shipmentsConfig.shipmentExternalEventForwarder(eventStore, shipmentSummaries, messageBus);
        shipmentsConfig.fulfillmentServiceClient(eventStore, new SilentFulfillmentService());
    }

    @Test
    public void reserveStockWithEverythingAvailableDispatchesAndStoresStockReserved() {
        messageBus.publish(List.of(new ReserveStock(referenceId, available)));

        var reserved = new StockReserved(shipmentId, referenceId, available, now);

        published.shouldReceiveMessages(reserved);
        assertThat(storedEvents()).usingRecursiveComparison().isEqualTo(List.of(reserved));
    }

    @Test
    public void reserveStockWithAnUnavailableProductDispatchesProductWasOutOfStock() {
        var productItems = new Shipment.ProductItem[] {availableItem, unavailableItem};

        messageBus.publish(List.of(new ReserveStock(referenceId, productItems)));

        published.shouldReceiveMessages(new ProductWasOutOfStock(shipmentId, referenceId, productItems, now));
    }

    @Test
    public void requestFulfillmentDispatchesFulfillmentRequestedWithTheReservedItems() {
        reserve();

        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));

        published.shouldReceiveMessages(new FulfillmentRequested(shipmentId, available, now));
    }

    @Test
    public void confirmPackageSentDispatchesPackageWasSentWithTheCarrierAndTrackingNumber() {
        reserve();
        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
        published.reset();

        messageBus.publish(List.of(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)));

        published.shouldReceiveMessages(new PackageWasSent(shipmentId, carrier, trackingNumber, now));
    }

    @Test
    public void requestFulfillmentWithoutAReservationStoresNothingAndDoesNotThrow() {
        assertThatCode(() -> messageBus.publish(List.of(new RequestFulfillment(shipmentId))))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
        assertThat(storedEvents()).isEmpty();
    }

    @Test
    public void deliverPackageDispatchesPackageWasDelivered() {
        reserve();
        dispatch();

        messageBus.publish(List.of(new DeliverPackage(shipmentId)));

        published.shouldReceiveMessages(new PackageWasDelivered(shipmentId, now));
    }

    @Test
    public void deliveringAnUnsentShipmentStoresNothingAndDoesNotThrow() {
        reserve();

        assertThatCode(() -> messageBus.publish(List.of(new DeliverPackage(shipmentId))))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void releaseStockDispatchesStockReleased() {
        reserve();

        messageBus.publish(List.of(new ReleaseStock(shipmentId)));

        published.shouldReceiveMessages(new StockReleased(shipmentId, now));
    }

    @Test
    public void releasingStockOfASentPackageStoresNothingAndDoesNotThrow() {
        reserve();
        dispatch();

        assertThatCode(() -> messageBus.publish(List.of(new ReleaseStock(shipmentId))))
                .doesNotThrowAnyException();

        published.shouldNotReceiveAnyEvent();
    }

    @Test
    public void rejectFulfillmentDispatchesFulfillmentRejected() {
        reserve();
        messageBus.publish(List.of(new RequestFulfillment(shipmentId)));
        published.reset();

        messageBus.publish(List.of(new RejectFulfillment(shipmentId)));

        published.shouldReceiveMessages(new FulfillmentRejected(shipmentId, now));
    }

    @Test
    public void reserveStockDerivesTheShipmentIdFromTheReferenceSoASecondReservationChangesNothing() {
        messageBus.publish(List.of(new ReserveStock(referenceId, available), new ReserveStock(referenceId, available)));

        assertThat(storedEvents())
                .usingRecursiveComparison()
                .isEqualTo(List.of(new StockReserved(shipmentId, referenceId, available, now)));
    }

    @Test
    public void mapsIdToItsOwnStream() {
        assertThat(ShipmentFacade.mapToStreamId(shipmentId)).isEqualTo("Shipment-%s".formatted(shipmentId.tail()));
    }

    private void reserve() {
        messageBus.publish(List.of(new ReserveStock(referenceId, available)));
        published.reset();
    }

    private void dispatch() {
        messageBus.publish(List.of(
                new RequestFulfillment(shipmentId), new ConfirmPackageSent(shipmentId, carrier, trackingNumber)));
        published.reset();
    }

    private List<Object> storedEvents() {
        return switch (eventStore.read(ShipmentFacade.mapToStreamId(shipmentId))) {
            case EventStore.ReadResult.Success success ->
                success.events().stream().map(EventEnvelope::data).toList();
            default -> List.of();
        };
    }
}
