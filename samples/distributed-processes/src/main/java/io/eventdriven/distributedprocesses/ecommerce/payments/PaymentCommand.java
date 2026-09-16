package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.time.OffsetDateTime;

public sealed interface PaymentCommand {
  record AuthorizePayment(
    String referenceId,
    double amount) implements PaymentCommand {
  }

  record ConfirmPaymentAuthorization(
    PaymentId paymentId
  ) implements PaymentCommand {
  }

  record CapturePayment(
    PaymentId paymentId
  ) implements PaymentCommand {
  }

  record VoidPayment(
    PaymentId paymentId
  ) implements PaymentCommand {
  }

  record RefundPayment(
    PaymentId paymentId
  ) implements PaymentCommand {
  }

  record DeclinePayment(
    PaymentId paymentId,
    DeclineReason reason
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
