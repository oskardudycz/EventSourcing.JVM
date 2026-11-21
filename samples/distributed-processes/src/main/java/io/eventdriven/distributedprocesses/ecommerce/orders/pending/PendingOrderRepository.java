package io.eventdriven.distributedprocesses.ecommerce.orders.pending;

import io.eventdriven.distributedprocesses.core.projections.ProjectionRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;

public interface PendingOrderRepository extends ProjectionRepository<PendingOrder, OrderId> {}
