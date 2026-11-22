package io.eventdriven.ecommerce.payments.external;

import io.eventdriven.core.projections.ProjectionRepository;
import io.eventdriven.ecommerce.payments.Payment;

public interface PaymentSummaryRepository extends ProjectionRepository<PaymentSummary, Payment.Id> {}
