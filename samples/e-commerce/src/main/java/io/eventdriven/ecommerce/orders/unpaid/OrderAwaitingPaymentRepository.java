package io.eventdriven.ecommerce.orders.unpaid;

import io.eventdriven.core.projections.ProjectionRepository;
import io.eventdriven.ecommerce.orders.Order;
import java.time.OffsetDateTime;
import java.util.List;

public interface OrderAwaitingPaymentRepository extends ProjectionRepository<OrderAwaitingPayment, Order.Id> {
    List<OrderAwaitingPayment> findByDueAtLessThanEqual(OffsetDateTime now);
}
