package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public sealed interface PaymentExternalEvent {
  String referenceId();

  @Schema(description = "The payment is authorized: the payment provider keeps the amount for the requester. The customer is not charged yet.")
  record PaymentAuthorized(
    String referenceId,
    PaymentId paymentId,
    OffsetDateTime authorizedAt ) implements PaymentExternalEvent {
  }

  @Schema(description = "The customer was charged.")
  record PaymentCaptured(
    String referenceId,
    PaymentId paymentId,
    OffsetDateTime capturedAt ) implements PaymentExternalEvent {
  }

  @Schema(description = "The payment failed: the customer did not authorize it in time, the authorization ran out, or charging the customer failed.")
  record PaymentFailed(
    String referenceId,
    PaymentId paymentId,
    OffsetDateTime failedAt ) implements PaymentExternalEvent {
  }
}
