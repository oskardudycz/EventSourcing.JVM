package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.net.URI;
import java.time.OffsetDateTime;

public sealed interface PaymentCommand {
  record AuthorizePayment(
    PaymentId paymentId,
    String referenceId,
    double amount) implements PaymentCommand {
  }

  record CreateCheckoutSession(
    PaymentId paymentId,
    String referenceId,
    URI successUrl,
    URI cancelUrl
  ) implements PaymentCommand {
  }

  record ConfirmPaymentAuthorization(
    PaymentId paymentId,
    OffsetDateTime captureBefore
  ) implements PaymentCommand {
  }

  record CapturePayment(
    PaymentId paymentId
  ) implements PaymentCommand {
  }

  record ConfirmPaymentCapture(
    PaymentId paymentId
  ) implements PaymentCommand {
  }

  record FailPaymentCapture(
    PaymentId paymentId,
    CaptureFailureReason reason
  ) implements PaymentCommand {
  }

  record CancelPayment(
    PaymentId paymentId
  ) implements PaymentCommand {
  }


  record TimeOutPayment(
    PaymentId paymentId,
    OffsetDateTime timedOutAt
  ) implements PaymentCommand {
  }

  record ExpirePaymentAuthorization(
    PaymentId paymentId,
    OffsetDateTime expiredAt
  ) implements PaymentCommand {
  }
}
