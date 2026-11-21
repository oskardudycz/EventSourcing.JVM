package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.OffsetDateTime;

public sealed interface PaymentCommand {
    PaymentId paymentId();

    @Schema(
            description =
                    "Asks for a payment of the amount. The payment waits until the customer authorizes it on the payment provider's page.")
    record AuthorizePayment(PaymentId paymentId, String referenceId, double amount) implements PaymentCommand {}

    @Schema(description = "Opens the payment provider's page, where the customer pays.")
    record CreateCheckoutSession(PaymentId paymentId, String referenceId, URI successUrl, URI cancelUrl)
            implements PaymentCommand {}

    @Schema(
            description =
                    "The payment provider's answer: the payment is authorized. The provider keeps the amount for the shop until the date by which the shop must charge the customer.")
    record ConfirmPaymentAuthorization(PaymentId paymentId, OffsetDateTime captureBefore) implements PaymentCommand {}

    @Schema(description = "Asks to charge the customer the authorized amount.")
    record CapturePayment(PaymentId paymentId) implements PaymentCommand {}

    @Schema(description = "The payment provider's answer: the customer was charged.")
    record ConfirmPaymentCapture(PaymentId paymentId) implements PaymentCommand {}

    @Schema(description = "Records that charging the customer failed, and why.")
    record FailPaymentCapture(PaymentId paymentId, CaptureFailureReason reason) implements PaymentCommand {}

    @Schema(description = "Cancels an authorized payment before the customer is charged.")
    record CancelPayment(PaymentId paymentId) implements PaymentCommand {}

    @Schema(description = "Fails a payment that the customer did not pay in time.")
    record TimeOutPayment(PaymentId paymentId, OffsetDateTime timedOutAt) implements PaymentCommand {}

    @Schema(description = "Fails a payment whose authorization ran out before the shop charged the customer.")
    record ExpirePaymentAuthorization(PaymentId paymentId, OffsetDateTime expiredAt) implements PaymentCommand {}
}
