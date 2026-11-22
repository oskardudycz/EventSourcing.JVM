package io.eventdriven.ecommerce.payments.authorization;

import io.eventdriven.core.projections.ProjectionRepository;
import io.eventdriven.ecommerce.payments.Payment;
import java.time.OffsetDateTime;
import java.util.List;

public interface AuthorizedPaymentRepository extends ProjectionRepository<AuthorizedPayment, Payment.Id> {
    List<AuthorizedPayment> findByCaptureBeforeLessThanEqual(OffsetDateTime now);
}
