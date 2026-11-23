package io.eventdriven.ecommerce.payments;

import static io.eventdriven.testing.contracts.Contracts.at;
import static io.eventdriven.testing.contracts.Contracts.options;
import static io.eventdriven.testing.contracts.Contracts.uuid;

import io.eventdriven.ecommerce.orders.Order;
import io.eventdriven.ecommerce.payments.PaymentCommand.*;
import io.eventdriven.ecommerce.payments.PaymentEvent.*;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.strictland.MessageContract;
import io.eventdriven.strictland.SnapshotVariant;
import io.eventdriven.testing.contracts.Contracts;
import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class PaymentContractTests {
    private static final Payment.Id paymentId = Payment.Id.of(uuid(1));
    private static final String referenceId = Order.Id.of(uuid(2)).value();

    @Nested
    class StoredEvent {
        static Stream<Named<Object>> examples() {
            return Contracts.examples(
                    new PaymentAuthorizationRequested(paymentId, referenceId, 129.99, at, at.plusMinutes(30)),
                    new CheckoutSessionCreated(
                            paymentId, URI.create("https://checkout.example.com/session/1"), at.plusMinutes(30)),
                    new PaymentAuthorized(paymentId, at, at.plusDays(7)),
                    new PaymentCaptureRequested(paymentId, 129.99, at),
                    new PaymentEvent.PaymentCaptured(paymentId, 129.99, at),
                    new PaymentCaptureFailed(paymentId, Payment.CaptureFailureReason.UnexpectedError, at),
                    new PaymentCanceled(paymentId, at),
                    new PaymentTimedOut(paymentId, at),
                    new PaymentAuthorizationExpired(paymentId, at));
        }

        @ParameterizedTest
        @MethodSource("examples")
        void keepsItsFormat(Object event) {
            MessageContract.specification(options).given(event).whenSerialized().thenContractIsUnchanged();
        }
    }

    @Nested
    class CommandOnTheBus {
        static Stream<Named<Object>> examples() {
            return Contracts.examples(
                    new AuthorizePayment(paymentId, referenceId, 129.99, at, at.plusMinutes(30)),
                    new CapturePayment(paymentId, at),
                    new CancelPayment(paymentId, at),
                    new ConfirmPaymentAuthorization(paymentId, at.plusDays(7), at),
                    new ConfirmPaymentCapture(paymentId, at),
                    new FailPaymentCapture(paymentId, Payment.CaptureFailureReason.UnexpectedError, at),
                    new TimeOutPayment(paymentId, at),
                    new ExpirePaymentAuthorization(paymentId, at));
        }

        static Stream<Named<Object>> examplesWithRequiredData() {
            return Contracts.examples(
                    new AuthorizePayment(paymentId, referenceId, 129.99),
                    new CapturePayment(paymentId),
                    new CancelPayment(paymentId),
                    new ConfirmPaymentAuthorization(paymentId, at.plusDays(7)),
                    new ConfirmPaymentCapture(paymentId),
                    new FailPaymentCapture(paymentId, Payment.CaptureFailureReason.UnexpectedError));
        }

        @ParameterizedTest
        @MethodSource("examplesWithRequiredData")
        void keepsItsFormatWithOnlyTheRequiredData(Object command) {
            MessageContract.specification(options)
                    .given(command)
                    .whenSerializedAs(SnapshotVariant.named("WithRequiredData"))
                    .thenContractIsUnchanged();
        }

        @ParameterizedTest
        @MethodSource("examples")
        void keepsItsFormat(Object command) {
            MessageContract.specification(options)
                    .given(command)
                    .whenSerialized()
                    .thenContractIsUnchanged();
        }
    }

    @Nested
    class PublishedEvent {
        static Stream<Named<Object>> examples() {
            return Contracts.examples(
                    new PaymentExternalEvent.PaymentAuthorized(referenceId, paymentId, at),
                    new PaymentExternalEvent.PaymentCaptured(referenceId, paymentId, at),
                    new PaymentExternalEvent.PaymentFailed(referenceId, paymentId, at));
        }

        @ParameterizedTest
        @MethodSource("examples")
        void keepsItsFormat(Object event) {
            MessageContract.specification(options).given(event).whenSerialized().thenContractIsUnchanged();
        }
    }
}
