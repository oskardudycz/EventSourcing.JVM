package io.eventdriven.ecommerce.orders.getcustomerorders;

import io.eventdriven.core.projections.ProjectionRepository;
import io.eventdriven.ecommerce.orders.Order;
import java.util.List;
import java.util.UUID;

public interface CustomerOrderRepository extends ProjectionRepository<CustomerOrder, Order.Id> {
    List<CustomerOrder> findByCustomerIdOrderByPlacedAtDesc(UUID customerId);
}
