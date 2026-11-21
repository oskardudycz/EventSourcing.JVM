package io.eventdriven.distributedprocesses.ecommerce.shipments.external;

import io.eventdriven.distributedprocesses.core.projections.ProjectionRepository;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;

public interface ShipmentSummaryRepository extends ProjectionRepository<ShipmentSummary, ShipmentId> {}
