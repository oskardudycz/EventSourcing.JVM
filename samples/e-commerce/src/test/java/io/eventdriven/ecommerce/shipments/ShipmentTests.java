package io.eventdriven.ecommerce.shipments;

import static io.eventdriven.ecommerce.shipments.ShipmentCommand.*;
import static io.eventdriven.ecommerce.shipments.ShipmentDecider.decide;
import static io.eventdriven.ecommerce.shipments.ShipmentEvent.*;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.testing.eventsourcing.EventSourcedSpecification;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ShipmentTests extends EventSourcedSpecification<Shipment, ShipmentEvent> {
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Shipment.Id shipmentId = Shipment.Id.derivedFrom(referenceId);
    private final Shipment.ProductItem availableItem = new Shipment.ProductItem(UUID.randomUUID(), 2);
    private final Shipment.ProductItem unavailableItem = new Shipment.ProductItem(UUID.randomUUID(), 1);
    private final Shipment.ProductItem[] available = new Shipment.ProductItem[] {availableItem};
    private final Shipment.ProductItem[] partlyAvailable = new Shipment.ProductItem[] {availableItem, unavailableItem};
    private final OffsetDateTime now = OffsetDateTime.now();
    private final String carrier = "DHL";
    private final String trackingNumber = "JD014600006281234567";

    private final StockReserved reserved = new StockReserved(shipmentId, referenceId, available, now);
    private final FulfillmentRequested fulfillmentRequested = new FulfillmentRequested(shipmentId, available, now);
    private final FulfillmentRejected rejected = new FulfillmentRejected(shipmentId, now);
    private final PackageWasSent sent = new PackageWasSent(shipmentId, carrier, trackingNumber, now);

    protected ShipmentTests() {
        super(Shipment::initial, Shipment::evolve);
    }

    @Test
    public void reservingWithEveryProductAvailableEmitsStockReserved() {
        // Given
        given()
                // When
                .when(current -> decide(new ReserveStock(referenceId, available, true, now), current))
                // Then
                .then(reserved);
    }

    @Test
    public void reservingWithAnUnavailableProductEmitsProductWasOutOfStock() {
        // Given
        given()
                // When
                .when(current -> decide(new ReserveStock(referenceId, partlyAvailable, false, now), current))
                // Then
                .then(new ProductWasOutOfStock(shipmentId, referenceId, partlyAvailable, now));
    }

    @Test
    public void reservingAnAlreadyReservedShipmentEmitsNothing() {
        // Given
        given(reserved)
                // When
                .when(current -> decide(new ReserveStock(referenceId, available, true, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void sendingAReservedShipmentEmitsFulfillmentRequested() {
        // Given
        given(reserved)
                // When
                .when(current -> decide(new RequestFulfillment(shipmentId, now), current))
                // Then
                .then(fulfillmentRequested);
    }

    @Test
    public void requestingFulfillmentTwiceEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested)
                // When
                .when(current -> decide(new RequestFulfillment(shipmentId, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void sendingWithoutAReservationEmitsNothing() {
        // Given
        given()
                // When
                .when(current -> decide(new RequestFulfillment(shipmentId, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void sendingAnAlreadySentPackageEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, sent)
                // When
                .when(current -> decide(new RequestFulfillment(shipmentId, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingThePackageSentEmitsPackageWasSentWithTheCarrierAndTrackingNumber() {
        // Given
        given(reserved, fulfillmentRequested)
                // When
                .when(current -> decide(new ConfirmPackageSent(shipmentId, carrier, trackingNumber, now), current))
                // Then
                .then(sent);
    }

    @Test
    public void confirmingAPackageSentWithoutAFulfillmentRequestEmitsNothing() {
        // Given
        given(reserved)
                // When
                .when(current -> decide(new ConfirmPackageSent(shipmentId, carrier, trackingNumber, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingThePackageSentTwiceEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, sent)
                // When
                .when(current -> decide(new ConfirmPackageSent(shipmentId, carrier, trackingNumber, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingThePackageSentAfterTheStockWasReleasedEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, new StockReleased(shipmentId, now))
                // When
                .when(current -> decide(new ConfirmPackageSent(shipmentId, carrier, trackingNumber, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void deliveringSentPackageEmitsPackageWasDelivered() {
        // Given
        given(reserved, fulfillmentRequested, sent)
                // When
                .when(current -> decide(new DeliverPackage(shipmentId, now), current))
                // Then
                .then(new PackageWasDelivered(shipmentId, now));
    }

    @Test
    public void deliveringAReservedButUnsentShipmentEmitsNothing() {
        // Given
        given(reserved)
                // When
                .when(current -> decide(new DeliverPackage(shipmentId, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void deliveringAlreadyDeliveredPackageEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, sent, new PackageWasDelivered(shipmentId, now))
                // When
                .when(current -> decide(new DeliverPackage(shipmentId, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void releasingAReservedShipmentEmitsStockReleased() {
        // Given
        given(reserved)
                // When
                .when(current -> decide(new ReleaseStock(shipmentId, now), current))
                // Then
                .then(new StockReleased(shipmentId, now));
    }

    @Test
    public void releasingStockWaitingForFulfillmentEmitsStockReleased() {
        // Given
        given(reserved, fulfillmentRequested)
                // When
                .when(current -> decide(new ReleaseStock(shipmentId, now), current))
                // Then
                .then(new StockReleased(shipmentId, now));
    }

    @Test
    public void releasingASentPackageEmitsNothingBecauseTheGoodsHaveLeft() {
        // Given
        given(reserved, fulfillmentRequested, sent)
                // When
                .when(current -> decide(new ReleaseStock(shipmentId, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void releasingTwiceEmitsNothing() {
        // Given
        given(reserved, new StockReleased(shipmentId, now))
                // When
                .when(current -> decide(new ReleaseStock(shipmentId, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void rejectingARequestedFulfillmentEmitsFulfillmentRejected() {
        // Given
        given(reserved, fulfillmentRequested)
                // When
                .when(current -> decide(new RejectFulfillment(shipmentId, now), current))
                // Then
                .then(rejected);
    }

    @Test
    public void rejectingWithoutAFulfillmentRequestEmitsNothing() {
        // Given
        given(reserved)
                // When
                .when(current -> decide(new RejectFulfillment(shipmentId, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void rejectingAfterThePackageWasSentEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, sent)
                // When
                .when(current -> decide(new RejectFulfillment(shipmentId, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void rejectingTwiceEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, rejected)
                // When
                .when(current -> decide(new RejectFulfillment(shipmentId, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingThePackageSentAfterARejectionEmitsNothing() {
        // Given
        given(reserved, fulfillmentRequested, rejected)
                // When
                .when(current -> decide(new ConfirmPackageSent(shipmentId, carrier, trackingNumber, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void releasingStockAfterARejectionEmitsStockReleased() {
        // Given
        given(reserved, fulfillmentRequested, rejected)
                // When
                .when(current -> decide(new ReleaseStock(shipmentId, now), current))
                // Then
                .then(new StockReleased(shipmentId, now));
    }
}
