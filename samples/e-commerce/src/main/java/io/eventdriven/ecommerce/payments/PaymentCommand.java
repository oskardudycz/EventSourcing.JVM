package io.eventdriven.ecommerce.payments;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;

public sealed interface PaymentCommand {
    Payment.Id paymentId();

    @Schema(
            description =
                    "Asks for a payment of the amount. The payment waits until the customer authorizes it on the payment provider's page.")
    record AuthorizePayment(
            Payment.Id paymentId,
            String referenceId,
            double amount,
            @Nullable OffsetDateTime now,
            @Nullable OffsetDateTime timesOutAt)
            implements PaymentCommand {
        public AuthorizePayment(Payment.Id paymentId, String referenceId, double amount) {
            this(paymentId, referenceId, amount, null, null);
        }
    }

    @Schema(
            description =
                    "The payment provider's answer: the payment is authorized. The provider keeps the amount for the shop until the date by which the shop must charge the customer.")
    record ConfirmPaymentAuthorization(
            Payment.Id paymentId,
            OffsetDateTime captureBefore,
            @Nullable OffsetDateTime now) implements PaymentCommand {
        public ConfirmPaymentAuthorization(Payment.Id paymentId, OffsetDateTime captureBefore) {
            this(paymentId, captureBefore, null);
        }
    }

    @Schema(description = "Asks to charge the customer the authorized amount.")
    record CapturePayment(Payment.Id paymentId, @Nullable OffsetDateTime now) implements PaymentCommand {
        public CapturePayment(Payment.Id paymentId) {
            this(paymentId, null);
        }
    }

    @Schema(description = "The payment provider's answer: the customer was charged.")
    record ConfirmPaymentCapture(
            Payment.Id paymentId, @Nullable OffsetDateTime now) implements PaymentCommand {
        public ConfirmPaymentCapture(Payment.Id paymentId) {
            this(paymentId, null);
        }
    }

    @Schema(description = "Records the checkout session that the payment provider opened for the payment.")
    record RecordCheckoutSession(
            Payment.Id paymentId,
            PaymentGateway.CheckoutSession session,
            @Nullable OffsetDateTime now) implements PaymentCommand {
        public RecordCheckoutSession(Payment.Id paymentId, PaymentGateway.CheckoutSession session) {
            this(paymentId, session, null);
        }
    }

    @Schema(description = "Records that charging the customer failed, and why.")
    record FailPaymentCapture(
            Payment.Id paymentId,
            Payment.CaptureFailureReason reason,
            @Nullable OffsetDateTime now) implements PaymentCommand {
        public FailPaymentCapture(Payment.Id paymentId, Payment.CaptureFailureReason reason) {
            this(paymentId, reason, null);
        }
    }

    @Schema(description = "Cancels an authorized payment before the customer is charged.")
    record CancelPayment(Payment.Id paymentId, @Nullable OffsetDateTime now) implements PaymentCommand {
        public CancelPayment(Payment.Id paymentId) {
            this(paymentId, null);
        }
    }

    @Schema(description = "Fails a payment that the customer did not pay in time.")
    record TimeOutPayment(Payment.Id paymentId, OffsetDateTime timedOutAt) implements PaymentCommand {}

    @Schema(description = "Fails a payment whose authorization ran out before the shop charged the customer.")
    record ExpirePaymentAuthorization(Payment.Id paymentId, OffsetDateTime expiredAt) implements PaymentCommand {}
}
