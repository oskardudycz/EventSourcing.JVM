package io.eventdriven.distributedprocesses.ecommerce.orders.unpaid;

import io.eventdriven.distributedprocesses.core.projections.ProjectionRepository;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import java.time.OffsetDateTime;
import java.util.List;

public interface OrderAwaitingPaymentRepository extends ProjectionRepository<OrderAwaitingPayment, OrderId> {
    List<OrderAwaitingPayment> findByDueAtLessThanEqual(OffsetDateTime now);
}
