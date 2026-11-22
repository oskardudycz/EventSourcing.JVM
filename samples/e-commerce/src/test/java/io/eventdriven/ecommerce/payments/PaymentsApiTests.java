package io.eventdriven.ecommerce.payments;

import static org.assertj.core.api.Assertions.assertThat;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.ecommerce.OutsideWorld;
import io.eventdriven.ecommerce.Time;
import io.eventdriven.ecommerce.payments.PaymentCommand.AuthorizePayment;
import io.eventdriven.ecommerce.payments.PaymentCommand.CancelPayment;
import io.eventdriven.ecommerce.payments.PaymentCommand.CapturePayment;
import io.eventdriven.ecommerce.payments.PaymentCommand.ConfirmPaymentAuthorization;
import io.eventdriven.ecommerce.payments.PaymentEvent.PaymentAuthorizationExpired;
import io.eventdriven.ecommerce.payments.PaymentEvent.PaymentAuthorizationRequested;
import io.eventdriven.ecommerce.payments.PaymentEvent.PaymentCanceled;
import io.eventdriven.ecommerce.payments.PaymentEvent.PaymentCaptureFailed;
import io.eventdriven.ecommerce.payments.PaymentEvent.PaymentCaptureRequested;
import io.eventdriven.ecommerce.payments.PaymentEvent.PaymentTimedOut;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent;
import io.eventdriven.ecommerce.payments.external.PaymentExternalEvent.PaymentFailed;
import io.eventdriven.testing.api.ApiE2ESpecification;
import io.eventdriven.testing.eventstore.RecordedEventsConfiguration;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The payments module takes its commands from the bus: the orders module asks it to authorize, capture and cancel, and
 * the payment provider answers. These tests send those messages, let time pass for the timeouts, and check what the
 * module stores, what it tells the others and what it asks the provider.
 */
@ActiveProfiles("in-memory")
@SpringBootTest
@Import({OutsideWorld.class, RecordedEventsConfiguration.class})
public class PaymentsApiTests extends ApiE2ESpecification {
    private static final Duration paymentValidity = Duration.ofHours(24);
    private static final Duration authorizationValidity = Duration.ofDays(7);
    private static final double amount = 62.5;

    @Autowired
    private Time time;

    @Autowired
    private PaymentProvider paymentProvider;

    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final Payment.Id paymentId = Payment.Id.derivedFrom(referenceId);

    private OffsetDateTime requestedAt;
    private OffsetDateTime captureBefore;
    private TestStep[] requestedPayment;
    private TestStep[] authorizedPayment;
    private TestStep[] capturedPayment;

    @BeforeEach
    public void describeThePayments() {
        requestedAt = time.now();
        captureBefore = requestedAt.plus(authorizationValidity);
        requestedPayment = new TestStep[] {send(new AuthorizePayment(paymentId, referenceId, amount))};
        authorizedPayment = scenario(requestedPayment, send(new ConfirmPaymentAuthorization(paymentId, captureBefore)));
        capturedPayment = scenario(authorizedPayment, send(new CapturePayment(paymentId)));
    }

    @Nested
    public class AuthorizingPayment {
        @Test
        public void authorizePayment_recordsTheRequestAndAsksTheProviderForNothing_untilTheCustomerPays() {
            given().when(send(new AuthorizePayment(paymentId, referenceId, amount)))
                    .then(
                            expectAppended(new PaymentAuthorizationRequested(
                                    paymentId, referenceId, amount, requestedAt, requestedAt.plus(paymentValidity))),
                            expectNothingPublished(),
                            outcome -> assertThat(paymentProvider.callsFor(paymentId))
                                    .isEmpty());
        }

        @Test
        public void authorizePayment_changesNothing_forTheSameReferenceAgain() {
            given(requestedPayment)
                    .when(send(new AuthorizePayment(paymentId, referenceId, amount)))
                    .then(expectNothingAppended(), expectNothingPublished());
        }

        @Test
        public void confirmPaymentAuthorization_tellsTheRequesterThePaymentIsAuthorized() {
            given(requestedPayment)
                    .when(send(new ConfirmPaymentAuthorization(paymentId, captureBefore)))
                    .then(
                            expectAppended(new PaymentEvent.PaymentAuthorized(paymentId, requestedAt, captureBefore)),
                            expectPublished(
                                    new PaymentExternalEvent.PaymentAuthorized(referenceId, paymentId, requestedAt)));
        }

        @Test
        public void confirmPaymentAuthorization_changesNothing_forAnAuthorizedPayment() {
            given(authorizedPayment)
                    .when(send(new ConfirmPaymentAuthorization(paymentId, captureBefore)))
                    .then(expectNothingAppended(), expectNothingPublished());
        }
    }

    @Nested
    public class CapturingPayment {
        @Test
        public void capturePayment_asksTheProviderAndTellsTheRequesterOnlyOnceTheProviderConfirmed() {
            given(authorizedPayment)
                    .when(send(new CapturePayment(paymentId)))
                    .then(
                            expectAppended(
                                    new PaymentCaptureRequested(paymentId, amount, requestedAt),
                                    new PaymentEvent.PaymentCaptured(paymentId, amount, requestedAt)),
                            expectPublished(
                                    new PaymentExternalEvent.PaymentCaptured(referenceId, paymentId, requestedAt)),
                            outcome -> assertThat(paymentProvider.callsFor(paymentId))
                                    .contains("capture:" + amount));
        }

        @Test
        public void capturePayment_failsThePayment_whenTheProviderRefusesTheCapture() {
            given(scenario(authorizedPayment, paymentProvider.refusesTheFirstCaptureOf(paymentId)))
                    .when(send(new CapturePayment(paymentId)))
                    .then(
                            expectAppended(
                                    new PaymentCaptureRequested(paymentId, amount, requestedAt),
                                    new PaymentCaptureFailed(
                                            paymentId, Payment.CaptureFailureReason.UnexpectedError, requestedAt)),
                            expectPublished(new PaymentFailed(referenceId, paymentId, requestedAt)));
        }
    }

    @Nested
    public class CancelingPayment {
        @Test
        public void cancelPayment_releasesTheHoldAtTheProviderWithoutTellingAnyone_forAnAuthorizedPayment() {
            given(authorizedPayment)
                    .when(send(new CancelPayment(paymentId)))
                    .then(
                            expectAppended(new PaymentCanceled(paymentId, requestedAt)),
                            expectNothingPublished(),
                            outcome -> assertThat(paymentProvider.callsFor(paymentId))
                                    .contains("cancel"));
        }

        @Test
        public void cancelPayment_keepsTheCancellation_whenTheProviderRefusesToCancel() {
            given(scenario(authorizedPayment, paymentProvider.refusesEverythingAfterAuthorizing(paymentId)))
                    .when(send(new CancelPayment(paymentId)))
                    .then(expectAppended(new PaymentCanceled(paymentId, requestedAt)), expectNothingPublished());
        }

        @Test
        public void cancelPayment_changesNothing_forACapturedPayment() {
            given(capturedPayment)
                    .when(send(new CancelPayment(paymentId)))
                    .then(expectNothingAppended(), expectNothingPublished());
        }
    }

    @Nested
    public class TimingOut {
        @Test
        public void aPaymentTheCustomerNeverPaysTimesOutAndFails_onceItsValidityPasses() {
            var timedOutAt = requestedAt.plus(paymentValidity);

            given(requestedPayment)
                    .when(time.passes(paymentValidity))
                    .then(
                            expectAppended(new PaymentTimedOut(paymentId, timedOutAt)),
                            expectPublished(new PaymentFailed(referenceId, paymentId, timedOutAt)),
                            outcome -> assertThat(paymentProvider.callsFor(paymentId))
                                    .contains("expire-checkout-session"));
        }

        @Test
        public void aPaymentTimesOut_evenWhenTheProviderRefusesToExpireTheCheckoutSession() {
            var timedOutAt = requestedAt.plus(paymentValidity);

            given(scenario(requestedPayment, paymentProvider.isUnreachableFor(paymentId)))
                    .when(time.passes(paymentValidity))
                    .then(
                            expectAppended(new PaymentTimedOut(paymentId, timedOutAt)),
                            expectPublished(new PaymentFailed(referenceId, paymentId, timedOutAt)));
        }

        @Test
        public void aPaymentDoesNotTimeOut_beforeItsValidityPasses() {
            given(requestedPayment)
                    .when(time.passes(paymentValidity.minusMinutes(1)))
                    .then(expectNothingAppended(), expectNothingPublished());
        }

        @Test
        public void aTimedOutPaymentTimesOutOnlyOnce_whenTimePassesAgain() {
            given(scenario(requestedPayment, time.passes(paymentValidity)))
                    .when(time.passes(Duration.ofMinutes(1)))
                    .then(expectNothingAppended(), expectNothingPublished());
        }

        @Test
        public void anAuthorizedPaymentNeverTimesOut() {
            given(authorizedPayment)
                    .when(time.passes(paymentValidity))
                    .then(expectNothingAppended(), expectNothingPublished());
        }
    }

    @Nested
    public class ExpiringAuthorization {
        @Test
        public void aHoldThatIsNeverCapturedExpiresAndFailsThePayment_onceItsDeadlinePasses() {
            given(authorizedPayment)
                    .when(time.passes(authorizationValidity))
                    .then(
                            expectAppended(new PaymentAuthorizationExpired(paymentId, captureBefore)),
                            expectPublished(new PaymentFailed(referenceId, paymentId, captureBefore)));
        }

        @Test
        public void aHoldDoesNotExpire_beforeItsDeadline() {
            given(authorizedPayment)
                    .when(time.passes(authorizationValidity.minusMinutes(1)))
                    .then(expectNothingAppended(), expectNothingPublished());
        }

        @Test
        public void anExpiredHoldExpiresOnlyOnce_whenTimePassesAgain() {
            given(scenario(authorizedPayment, time.passes(authorizationValidity)))
                    .when(time.passes(Duration.ofDays(1)))
                    .then(expectNothingAppended(), expectNothingPublished());
        }

        @Test
        public void aCanceledPaymentNeverExpires() {
            given(scenario(authorizedPayment, send(new CancelPayment(paymentId))))
                    .when(time.passes(authorizationValidity))
                    .then(expectNothingAppended(), expectNothingPublished());
        }

        @Test
        public void aCapturedPaymentNeverExpires() {
            given(capturedPayment)
                    .when(time.passes(authorizationValidity))
                    .then(expectNothingAppended(), expectNothingPublished());
        }

        @Test
        public void capturePayment_changesNothing_afterTheHoldExpired() {
            given(scenario(authorizedPayment, time.passes(authorizationValidity)))
                    .when(send(new CapturePayment(paymentId)))
                    .then(expectNothingAppended(), expectNothingPublished());
        }
    }

    @Override
    protected boolean isAbout(Object message) {
        return (message instanceof PaymentEvent || message instanceof PaymentExternalEvent)
                && message.toString().contains(paymentId.toString());
    }
}
