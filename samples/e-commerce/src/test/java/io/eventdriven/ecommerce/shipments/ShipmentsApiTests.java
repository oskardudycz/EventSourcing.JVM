package io.eventdriven.ecommerce.shipments;

import static io.eventdriven.ecommerce.shipments.Warehouse.carrier;
import static io.eventdriven.ecommerce.shipments.Warehouse.deliversThePackage;
import static io.eventdriven.ecommerce.shipments.Warehouse.rejectsTheRequest;
import static io.eventdriven.ecommerce.shipments.Warehouse.sendsThePackage;
import static io.eventdriven.ecommerce.shipments.Warehouse.trackingNumber;
import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.ecommerce.OutsideWorld;
import io.eventdriven.ecommerce.Time;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.ReleaseStock;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.RequestFulfillment;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.ReserveStock;
import io.eventdriven.ecommerce.shipments.ShipmentEvent.FulfillmentRejected;
import io.eventdriven.ecommerce.shipments.ShipmentEvent.FulfillmentRequested;
import io.eventdriven.ecommerce.shipments.ShipmentEvent.PackageWasDelivered;
import io.eventdriven.ecommerce.shipments.ShipmentEvent.ProductWasOutOfStock;
import io.eventdriven.ecommerce.shipments.ShipmentEvent.StockReleased;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent.ShipmentFailed;
import io.eventdriven.testing.api.ApiE2ESpecification;
import io.eventdriven.testing.eventstore.RecordedEventsConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The shipments module has no web API: the orders module and the fulfillment service send it messages. These tests
 * send those messages on the bus and check what the module stores and what it tells the others.
 */
@ActiveProfiles("in-memory")
@SpringBootTest
@Import({OutsideWorld.class, RecordedEventsConfiguration.class})
public class ShipmentsApiTests extends ApiE2ESpecification {
    @Autowired
    private Time time;

    @Autowired
    private Warehouse warehouse;

    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Shipment.Id shipmentId = Shipment.Id.derivedFrom(referenceId);
    private final Shipment.ProductItem shoes = new Shipment.ProductItem(UUID.randomUUID(), 2);
    private final Shipment.ProductItem shirt = new Shipment.ProductItem(UUID.randomUUID(), 1);
    private final Shipment.ProductItem[] products = {shoes};

    private TestStep[] reservedStock;
    private TestStep[] requestedFulfillment;
    private TestStep[] sentPackage;

    @BeforeEach
    public void describeTheShipments() {
        reservedStock = new TestStep[] {send(new ReserveStock(referenceId, products))};
        requestedFulfillment = scenario(reservedStock, send(new RequestFulfillment(shipmentId)));
        sentPackage = scenario(requestedFulfillment, sendsThePackage(shipmentId));
    }

    @Nested
    public class ReservingStock {
        @Test
        public void reserveStock_setsTheProductsAsideAndTellsTheRequester_whenEverythingIsAvailable() {
            given().when(send(new ReserveStock(referenceId, products)))
                    .then(
                            expectAppended(
                                    new ShipmentEvent.StockReserved(shipmentId, referenceId, products, time.now())),
                            expectPublished(
                                    new ShipmentExternalEvent.StockReserved(shipmentId, referenceId, time.now())));
        }

        @Test
        public void reserveStock_failsTheShipment_whenAProductIsOutOfStock() {
            var productItems = new Shipment.ProductItem[] {shoes, shirt};

            given(warehouse.isOutOf(shirt.productId()))
                    .when(send(new ReserveStock(referenceId, productItems)))
                    .then(
                            expectAppended(new ProductWasOutOfStock(shipmentId, referenceId, productItems, time.now())),
                            expectPublished(new ShipmentFailed(
                                    shipmentId, referenceId, ShipmentFailed.Reason.ProductsOutOfStock, time.now())));
        }

        @Test
        public void reserveStock_changesNothing_forTheSameReferenceAgain() {
            given(reservedStock)
                    .when(send(new ReserveStock(referenceId, products)))
                    .then(expectNothingAppended(), expectNothingPublished());
        }
    }

    @Nested
    public class RequestingFulfillment {
        @Test
        public void requestFulfillment_asksTheFulfillmentServiceForTheReservedProducts() {
            given(reservedStock)
                    .when(send(new RequestFulfillment(shipmentId)))
                    .then(
                            expectAppended(new FulfillmentRequested(shipmentId, products, time.now())),
                            expectNothingPublished(),
                            outcome -> assertThat(warehouse.wasAskedToSend(shipmentId))
                                    .isTrue());
        }

        @Test
        public void requestFulfillment_changesNothing_withoutAReservation() {
            given().when(send(new RequestFulfillment(shipmentId)))
                    .then(expectNothingAppended(), expectNothingPublished());
        }
    }

    @Nested
    public class SendingThePackage {
        @Test
        public void confirmPackageSent_tellsTheRequesterTheCarrierAndTheTrackingNumber() {
            given(requestedFulfillment)
                    .when(sendsThePackage(shipmentId))
                    .then(
                            expectAppended(
                                    new ShipmentEvent.PackageWasSent(shipmentId, carrier, trackingNumber, time.now())),
                            expectPublished(new ShipmentExternalEvent.PackageWasSent(
                                    shipmentId, referenceId, carrier, trackingNumber, time.now())));
        }

        @Test
        public void rejectFulfillment_failsTheShipment_forARequestedFulfillment() {
            given(requestedFulfillment)
                    .when(rejectsTheRequest(shipmentId))
                    .then(
                            expectAppended(new FulfillmentRejected(shipmentId, time.now())),
                            expectPublished(new ShipmentFailed(
                                    shipmentId, referenceId, ShipmentFailed.Reason.FulfillmentRejected, time.now())));
        }
    }

    @Nested
    public class DeliveringThePackage {
        @Test
        public void deliverPackage_recordsTheDeliveryWithoutTellingAnyone_forASentPackage() {
            given(sentPackage)
                    .when(deliversThePackage(shipmentId))
                    .then(expectAppended(new PackageWasDelivered(shipmentId, time.now())), expectNothingPublished());
        }

        @Test
        public void deliverPackage_changesNothing_forAPackageThatWasNotSent() {
            given(reservedStock)
                    .when(deliversThePackage(shipmentId))
                    .then(expectNothingAppended(), expectNothingPublished());
        }
    }

    @Nested
    public class ReleasingStock {
        @Test
        public void releaseStock_putsTheProductsBackWithoutTellingAnyone_forReservedStock() {
            given(reservedStock)
                    .when(send(new ReleaseStock(shipmentId)))
                    .then(expectAppended(new StockReleased(shipmentId, time.now())), expectNothingPublished());
        }

        @Test
        public void releaseStock_changesNothing_forASentPackage() {
            given(sentPackage)
                    .when(send(new ReleaseStock(shipmentId)))
                    .then(expectNothingAppended(), expectNothingPublished());
        }
    }

    @Override
    protected boolean isAbout(Object message) {
        return (message instanceof ShipmentEvent || message instanceof ShipmentExternalEvent)
                && message.toString().contains(shipmentId.toString());
    }
}
