package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.projections.ProjectionRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import java.time.OffsetDateTime;
import java.util.List;

public interface PendingPaymentRepository extends ProjectionRepository<PendingPayment, PaymentId> {
    List<PendingPayment> findByTimesOutAtLessThanEqual(OffsetDateTime now);
}
