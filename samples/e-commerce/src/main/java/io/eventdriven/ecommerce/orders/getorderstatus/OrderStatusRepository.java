package io.eventdriven.ecommerce.orders.getorderstatus;

import io.eventdriven.core.projections.ProjectionRepository;
import io.eventdriven.ecommerce.orders.Order;

public interface OrderStatusRepository extends ProjectionRepository<OrderStatus, Order.Id> {}
