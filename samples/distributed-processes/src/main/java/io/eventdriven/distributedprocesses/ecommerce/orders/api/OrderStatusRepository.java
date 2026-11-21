package io.eventdriven.distributedprocesses.ecommerce.orders.api;

import io.eventdriven.distributedprocesses.core.projections.ProjectionRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;

public interface OrderStatusRepository extends ProjectionRepository<OrderStatus, OrderId> {}
