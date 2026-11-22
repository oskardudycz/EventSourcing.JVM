package io.eventdriven.ecommerce.payments.authorization;

import io.eventdriven.core.projections.ProjectionRepository;
import io.eventdriven.ecommerce.payments.Payment;
import java.time.OffsetDateTime;
import java.util.List;

public interface PendingPaymentRepository extends ProjectionRepository<PendingPayment, Payment.Id> {
    List<PendingPayment> findByTimesOutAtLessThanEqual(OffsetDateTime now);
}
