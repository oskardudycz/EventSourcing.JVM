package io.eventdriven.ecommerce.orders.pending;

import io.eventdriven.core.projections.ProjectionRepository;
import io.eventdriven.ecommerce.orders.Order;

public interface PendingOrderRepository extends ProjectionRepository<PendingOrder, Order.Id> {}
