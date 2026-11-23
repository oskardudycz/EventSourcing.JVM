package io.eventdriven.ecommerce.shipments;

import static io.eventdriven.testing.contracts.Contracts.at;
import static io.eventdriven.testing.contracts.Contracts.options;
import static io.eventdriven.testing.contracts.Contracts.uuid;

import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.shipments.ShipmentCommand.*;
import io.eventdriven.ecommerce.shipments.ShipmentEvent.*;
import io.eventdriven.ecommerce.shipments.external.ShipmentExternalEvent;
import io.eventdriven.strictland.MessageContract;
import io.eventdriven.strictland.SnapshotVariant;
import io.eventdriven.testing.contracts.Contracts;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ShipmentContractTests {
    private static final Shipment.Id shipmentId = Shipment.Id.of(uuid(1));
    private static final String referenceId = Order.Id.of(uuid(2)).value();
    private static final Shipment.ProductItem[] productItems = {new Shipment.ProductItem(uuid(3), 2)};

    @Nested
    class StoredEvent {
        static Stream<Named<Object>> examples() {
            return Contracts.examples(
                    new ShipmentEvent.StockReserved(shipmentId, referenceId, productItems, at),
                    new ProductWasOutOfStock(shipmentId, referenceId, productItems, at),
                    new FulfillmentRequested(shipmentId, productItems, at),
                    new FulfillmentRejected(shipmentId, at),
                    new ShipmentEvent.PackageWasSent(shipmentId, "DHL", "TRACK-1", at),
                    new PackageWasDelivered(shipmentId, at),
                    new StockReleased(shipmentId, at));
        }

        @ParameterizedTest
        @MethodSource("examples")
        void keepsItsFormat(Object event) {
            MessageContract.specification(options).given(event).whenSerialized().thenContractIsUnchanged();
        }
    }

    @Nested
    class CommandOnTheBus {
        static Stream<Named<Object>> examples() {
            return Contracts.examples(
                    new ReserveStock(referenceId, productItems, true, at),
                    new RequestFulfillment(shipmentId, at),
                    new ReleaseStock(shipmentId, at),
                    new ConfirmPackageSent(shipmentId, "DHL", "TRACK-1", at),
                    new RejectFulfillment(shipmentId, at),
                    new DeliverPackage(shipmentId, at));
        }

        static Stream<Named<Object>> examplesWithRequiredData() {
            return Contracts.examples(
                    new ReserveStock(referenceId, productItems),
                    new RequestFulfillment(shipmentId),
                    new ReleaseStock(shipmentId),
                    new ConfirmPackageSent(shipmentId, "DHL", "TRACK-1"),
                    new RejectFulfillment(shipmentId),
                    new DeliverPackage(shipmentId));
        }

        @ParameterizedTest
        @MethodSource("examplesWithRequiredData")
        void keepsItsFormatWithOnlyTheRequiredData(Object command) {
            MessageContract.specification(options)
                    .given(command)
                    .whenSerializedAs(SnapshotVariant.named("WithRequiredData"))
                    .thenContractIsUnchanged();
        }

        @ParameterizedTest
        @MethodSource("examples")
        void keepsItsFormat(Object command) {
            MessageContract.specification(options)
                    .given(command)
                    .whenSerialized()
                    .thenContractIsUnchanged();
        }
    }

    @Nested
    class PublishedEvent {
        static Stream<Named<Object>> examples() {
            return Contracts.examples(
                    new ShipmentExternalEvent.StockReserved(shipmentId, referenceId, at),
                    new ShipmentExternalEvent.PackageWasSent(shipmentId, referenceId, "DHL", "TRACK-1", at),
                    new ShipmentExternalEvent.ShipmentFailed(
                            shipmentId,
                            referenceId,
                            ShipmentExternalEvent.ShipmentFailed.Reason.ProductsOutOfStock,
                            at));
        }

        @ParameterizedTest
        @MethodSource("examples")
        void keepsItsFormat(Object event) {
            MessageContract.specification(options).given(event).whenSerialized().thenContractIsUnchanged();
        }
    }
}
