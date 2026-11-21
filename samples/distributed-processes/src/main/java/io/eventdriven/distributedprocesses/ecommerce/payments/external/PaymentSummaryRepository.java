package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.core.projections.ProjectionRepository;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;

public interface PaymentSummaryRepository extends ProjectionRepository<PaymentSummary, PaymentId> {}
