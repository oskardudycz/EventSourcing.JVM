package io.eventdriven.ecommerce.shipments.external;

import static io.eventdriven.ecommerce.shipments.ShipmentEvent.*;

import io.eventdriven.core.projections.Projection;
import io.eventdriven.ecommerce.shipments.Shipment;
import java.util.List;
import org.jspecify.annotations.Nullable;

// What the forwarder adds to the internal events: the requester's id, which only the events that
// record the request carry.
public class ShipmentSummaries extends Projection<ShipmentSummary, Shipment.Id> {
    public ShipmentSummaries(ShipmentSummaryRepository repository) {
        super(repository);
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(StockReserved.class, ProductWasOutOfStock.class);
    }

    @Override
    protected Shipment.Id getDocumentId(Object event) {
        return switch (event) {
            case StockReserved e -> e.shipmentId();
            case ProductWasOutOfStock e -> e.shipmentId();
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
        };
    }

    @Override
    protected @Nullable ShipmentSummary evolve(@Nullable ShipmentSummary current, Object event) {
        return switch (event) {
            case StockReserved e -> new ShipmentSummary(e.shipmentId(), e.referenceId());
            case ProductWasOutOfStock e -> new ShipmentSummary(e.shipmentId(), e.referenceId());
            default -> current;
        };
    }
}
