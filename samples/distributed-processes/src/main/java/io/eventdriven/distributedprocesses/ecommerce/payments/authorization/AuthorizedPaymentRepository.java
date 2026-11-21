package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.projections.ProjectionRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import java.time.OffsetDateTime;
import java.util.List;

public interface AuthorizedPaymentRepository extends ProjectionRepository<AuthorizedPayment, PaymentId> {
    List<AuthorizedPayment> findByCaptureBeforeLessThanEqual(OffsetDateTime now);
}
