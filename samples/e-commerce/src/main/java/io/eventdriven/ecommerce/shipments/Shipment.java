package io.eventdriven.ecommerce.shipments;

import static io.eventdriven.ecommerce.shipments.ShipmentEvent.*;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.eventdriven.core.identifiers.EntityId;
import io.eventdriven.core.identifiers.Urns;
import jakarta.persistence.Embeddable;
import java.util.UUID;

public record Shipment(Id id, ProductItem[] productItems, Status status) {
    enum Status {
        Reserved,
        FulfillmentRequested,
        FulfillmentRejected,
        Sent,
        Delivered,
        Released,
        ProductsOutOfStock
    }

    public static Shipment initial() {
        return new Shipment(null, null, null);
    }

    boolean isStockHeld() {
        return status == Status.Reserved
                || status == Status.FulfillmentRequested
                || status == Status.FulfillmentRejected;
    }

    public static Shipment evolve(Shipment state, ShipmentEvent event) {
        return switch (event) {
            case StockReserved reserved ->
                new Shipment(reserved.shipmentId(), reserved.productItems(), Status.Reserved);
            case ProductWasOutOfStock outOfStock ->
                new Shipment(outOfStock.shipmentId(), state.productItems, Status.ProductsOutOfStock);
            case FulfillmentRequested ignored -> state.withStatus(Status.FulfillmentRequested);
            case FulfillmentRejected ignored -> state.withStatus(Status.FulfillmentRejected);
            case PackageWasSent ignored -> state.withStatus(Status.Sent);
            case PackageWasDelivered ignored -> state.withStatus(Status.Delivered);
            case StockReleased ignored -> state.withStatus(Status.Released);
        };
    }

    private Shipment withStatus(Status status) {
        return new Shipment(id, productItems, status);
    }

    @Embeddable
    public record Id(@JsonValue String value) implements EntityId {
        private static final String type = "shipment";

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Id {
            Urns.requireType(value, type);
        }

        public static Id of(UUID id) {
            return new Id(Urns.of("ecommerce", type, id));
        }

        public static Id derivedFrom(String sourceUrn) {
            return new Id(Urns.derive(sourceUrn, type));
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public record ProductItem(UUID productId, int quantity) {}
}
