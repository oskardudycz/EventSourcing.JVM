package io.eventdriven.distributedprocesses.ecommerce.shipments;

import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentEvent.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;
import io.eventdriven.testing.EventSourcedSpecification;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class ShipmentTests extends EventSourcedSpecification<Shipment, ShipmentEvent> {
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final ShipmentId shipmentId = ShipmentId.derivedFrom(referenceId);
    private final ProductItem availableItem = new ProductItem(UUID.randomUUID(), 2);
    private final ProductItem unavailableItem = new ProductItem(UUID.randomUUID(), 1);
    private final ProductItem[] available = new ProductItem[] {availableItem};
    private final ProductItem[] partlyAvailable = new ProductItem[] {availableItem, unavailableItem};
    private final Function<ProductItem, Boolean> isProductAvailable =
            item -> Set.of(availableItem).contains(item);
    private final OffsetDateTime now = OffsetDateTime.now();
    private final String carrier = "DHL";
    private final String trackingNumber = "JD014600006281234567";

    private final StockReserved reserved = new StockReserved(shipmentId, referenceId, available, now);
    private final FulfillmentRequested fulfillmentRequested = new FulfillmentRequested(shipmentId, available, now);
    private final FulfillmentRejected rejected = new FulfillmentRejected(shipmentId, now);
    private final PackageWasSent sent = new PackageWasSent(shipmentId, carrier, trackingNumber, now);

    private final ShipmentDecider decider = new ShipmentDecider(isProductAvailable, () -> now);

    protected ShipmentTests() {
        super(Shipment::initial, Shipment::evolve);
    }

    @Test
    public void reservingWithEveryProductAvailableEmitsStockReserved() {
        // Given
        given()
                // When
                .when(handling(new ReserveStock(referenceId, available)))
                // Then
                .then(reserved);
    }

    @Test
    public void reservingWithAnUnavailableProductEmitsProductWasOutOfStock() {
        // Given
        given()
                // When
                .when(handling(new ReserveStock(referenceId, partlyAvailable)))
                // Then
                .then(new ProductWasOutOfStock(shipmentId, referenceId, partlyAvailable, now));
    }

    @Test
    public void reservingAnAlreadyReservedShipmentEmitsNothing() {
        // Given
        given(reserved)
                // When
                .when(handling(new ReserveStock(referenceId, available)))
                // Then
                .thenNothing();
    }

    @Test
    public void sendingAReservedShipmentEmitsFulfillmentRequested() {
        // Given
        given(reserved)
                // When
                .when(handling(new RequestFulfillment(shipmentId)))
                // Then
                .then(fulfillmentRequested);
    }

    @Test
    public void requestingFulfillmentTwiceEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested)
                // When
                .when(handling(new RequestFulfillment(shipmentId)))
                // Then
                .thenNothing();
    }

    @Test
    public void sendingWithoutAReservationEmitsNothing() {
        // Given
        given()
                // When
                .when(handling(new RequestFulfillment(shipmentId)))
                // Then
                .thenNothing();
    }

    @Test
    public void sendingAnAlreadySentPackageEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, sent)
                // When
                .when(handling(new RequestFulfillment(shipmentId)))
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingThePackageSentEmitsPackageWasSentWithTheCarrierAndTrackingNumber() {
        // Given
        given(reserved, fulfillmentRequested)
                // When
                .when(handling(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)))
                // Then
                .then(sent);
    }

    @Test
    public void confirmingAPackageSentWithoutAFulfillmentRequestEmitsNothing() {
        // Given
        given(reserved)
                // When
                .when(handling(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)))
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingThePackageSentTwiceEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, sent)
                // When
                .when(handling(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)))
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingThePackageSentAfterTheStockWasReleasedEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, new StockReleased(shipmentId, now))
                // When
                .when(handling(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)))
                // Then
                .thenNothing();
    }

    @Test
    public void deliveringSentPackageEmitsPackageWasDelivered() {
        // Given
        given(reserved, fulfillmentRequested, sent)
                // When
                .when(handling(new DeliverPackage(shipmentId)))
                // Then
                .then(new PackageWasDelivered(shipmentId, now));
    }

    @Test
    public void deliveringAReservedButUnsentShipmentEmitsNothing() {
        // Given
        given(reserved)
                // When
                .when(handling(new DeliverPackage(shipmentId)))
                // Then
                .thenNothing();
    }

    @Test
    public void deliveringAlreadyDeliveredPackageEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, sent, new PackageWasDelivered(shipmentId, now))
                // When
                .when(handling(new DeliverPackage(shipmentId)))
                // Then
                .thenNothing();
    }

    @Test
    public void releasingAReservedShipmentEmitsStockReleased() {
        // Given
        given(reserved)
                // When
                .when(handling(new ReleaseStock(shipmentId)))
                // Then
                .then(new StockReleased(shipmentId, now));
    }

    @Test
    public void releasingStockWaitingForFulfillmentEmitsStockReleased() {
        // Given
        given(reserved, fulfillmentRequested)
                // When
                .when(handling(new ReleaseStock(shipmentId)))
                // Then
                .then(new StockReleased(shipmentId, now));
    }

    @Test
    public void releasingASentPackageEmitsNothingBecauseTheGoodsHaveLeft() {
        // Given
        given(reserved, fulfillmentRequested, sent)
                // When
                .when(handling(new ReleaseStock(shipmentId)))
                // Then
                .thenNothing();
    }

    @Test
    public void releasingTwiceEmitsNothing() {
        // Given
        given(reserved, new StockReleased(shipmentId, now))
                // When
                .when(handling(new ReleaseStock(shipmentId)))
                // Then
                .thenNothing();
    }

    @Test
    public void rejectingARequestedFulfillmentEmitsFulfillmentRejected() {
        // Given
        given(reserved, fulfillmentRequested)
                // When
                .when(handling(new RejectFulfillment(shipmentId)))
                // Then
                .then(rejected);
    }

    @Test
    public void rejectingWithoutAFulfillmentRequestEmitsNothing() {
        // Given
        given(reserved)
                // When
                .when(handling(new RejectFulfillment(shipmentId)))
                // Then
                .thenNothing();
    }

    @Test
    public void rejectingAfterThePackageWasSentEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, sent)
                // When
                .when(handling(new RejectFulfillment(shipmentId)))
                // Then
                .thenNothing();
    }

    @Test
    public void rejectingTwiceEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, rejected)
                // When
                .when(handling(new RejectFulfillment(shipmentId)))
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingThePackageSentAfterARejectionEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, rejected)
                // When
                .when(handling(new ConfirmPackageSent(shipmentId, carrier, trackingNumber)))
                // Then
                .thenNothing();
    }

    @Test
    public void releasingStockAfterARejectionEmitsStockReleased() {
        // Given
        given(reserved, fulfillmentRequested, rejected)
                // When
                .when(handling(new ReleaseStock(shipmentId)))
                // Then
                .then(new StockReleased(shipmentId, now));
    }

    @Test
    public void onlyTheReservationEventsStoreTheRequester() {
        var released = decide(
                new ReserveStock(referenceId, available),
                new RequestFulfillment(shipmentId),
                new RejectFulfillment(shipmentId),
                new ReleaseStock(shipmentId));
        var delivered = decide(
                new ReserveStock(referenceId, available),
                new RequestFulfillment(shipmentId),
                new ConfirmPackageSent(shipmentId, carrier, trackingNumber),
                new DeliverPackage(shipmentId));
        var outOfStock = decide(new ReserveStock(referenceId, partlyAvailable));

        assertThat(released)
                .usingRecursiveComparison()
                .isEqualTo(List.of(reserved, fulfillmentRequested, rejected, new StockReleased(shipmentId, now)));
        assertThat(delivered)
                .usingRecursiveComparison()
                .isEqualTo(List.of(reserved, fulfillmentRequested, sent, new PackageWasDelivered(shipmentId, now)));
        assertThat(outOfStock)
                .usingRecursiveComparison()
                .isEqualTo(List.of(new ProductWasOutOfStock(shipmentId, referenceId, partlyAvailable, now)));
    }

    private List<ShipmentEvent> decide(ShipmentCommand... commands) {
        var state = Shipment.initial();
        var events = new ArrayList<ShipmentEvent>();

        for (var command : commands) {
            for (var event : decider.handle(command, state)) {
                events.add(event);
                state = Shipment.evolve(state, event);
            }
        }

        return events;
    }

    private Function<Shipment, ShipmentEvent[]> handling(ShipmentCommand command) {
        return current -> decider.handle(command, current);
    }
}
