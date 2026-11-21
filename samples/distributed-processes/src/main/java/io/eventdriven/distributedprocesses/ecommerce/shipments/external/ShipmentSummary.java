package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

@Entity
public class ShipmentSummary implements Versioned {
    @EmbeddedId
    private ShipmentId shipmentId;

    private String referenceId;
    private long version;

    public ShipmentSummary() {}

    public ShipmentSummary(ShipmentId shipmentId, String referenceId) {
        this.shipmentId = shipmentId;
        this.referenceId = referenceId;
    }

    public ShipmentId getShipmentId() {
        return shipmentId;
    }

    public String getReferenceId() {
        return referenceId;
    }

    @Override
    public long getVersion() {
        return version;
    }

    @Override
    public void setVersion(long version) {
        this.version = version;
    }
}
