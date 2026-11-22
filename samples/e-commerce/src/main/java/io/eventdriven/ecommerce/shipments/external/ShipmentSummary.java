package io.eventdriven.ecommerce.shipments.external;

import io.eventdriven.core.projections.Versioned;
import io.eventdriven.ecommerce.shipments.Shipment;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

@Entity
public class ShipmentSummary implements Versioned {
    @EmbeddedId
    private Shipment.Id shipmentId;

    private String referenceId;
    private long version;

    public ShipmentSummary() {}

    public ShipmentSummary(Shipment.Id shipmentId, String referenceId) {
        this.shipmentId = shipmentId;
        this.referenceId = referenceId;
    }

    public Shipment.Id getShipmentId() {
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
