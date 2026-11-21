package io.eventdriven.distributedprocesses.ecommerce.orders.api;

import io.eventdriven.distributedprocesses.core.projections.ProjectionRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import java.util.List;
import java.util.UUID;

public interface CustomerOrderRepository extends ProjectionRepository<CustomerOrder, OrderId> {
    List<CustomerOrder> findByCustomerIdOrderByPlacedAtDesc(UUID customerId);
}
