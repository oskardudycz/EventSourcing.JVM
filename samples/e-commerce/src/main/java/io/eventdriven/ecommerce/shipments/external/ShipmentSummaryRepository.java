package io.eventdriven.ecommerce.shipments.external;

import io.eventdriven.core.projections.ProjectionRepository;
import io.eventdriven.ecommerce.shipments.Shipment;

public interface ShipmentSummaryRepository extends ProjectionRepository<ShipmentSummary, Shipment.Id> {}
