package io.eventdriven.ecommerce.payments;

import static io.eventdriven.ecommerce.payments.PaymentCommand.*;
import static io.eventdriven.ecommerce.payments.PaymentDecider.decide;
import static io.eventdriven.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.core.identifiers.Urns;
import io.eventdriven.testing.eventsourcing.EventSourcedSpecification;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class PaymentTests extends EventSourcedSpecification<Payment, PaymentEvent> {
    private final Payment.Id paymentId = Payment.Id.of(UUID.randomUUID());
    private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
    private final double amount = 62.5;
    private final OffsetDateTime now = OffsetDateTime.now();
    private final OffsetDateTime timesOutAt = now.plusMinutes(15);
    private final OffsetDateTime captureBefore = now.plusDays(7);
    private final PaymentGateway.CheckoutSession checkoutSession =
            new PaymentGateway.CheckoutSession(URI.create("https://checkout.example/c/pay/1"), now.plusMinutes(30));
    private final PaymentGateway.CheckoutSession nextCheckoutSession =
            new PaymentGateway.CheckoutSession(URI.create("https://checkout.example/c/pay/2"), now.plusMinutes(70));

    private final PaymentAuthorizationRequested authorizationRequested =
            new PaymentAuthorizationRequested(paymentId, referenceId, amount, now, timesOutAt);
    private final PaymentAuthorized authorized = new PaymentAuthorized(paymentId, now, captureBefore);
    private final PaymentCaptureRequested captureRequested = new PaymentCaptureRequested(paymentId, amount, now);
    private final PaymentCaptured captured = new PaymentCaptured(paymentId, amount, now);
    private final PaymentCaptureFailed captureFailed =
            new PaymentCaptureFailed(paymentId, Payment.CaptureFailureReason.UnexpectedError, now);
    private final PaymentCanceled canceled = new PaymentCanceled(paymentId, now);
    private final CheckoutSessionCreated checkoutSessionCreated =
            new CheckoutSessionCreated(paymentId, checkoutSession.url(), checkoutSession.expiresAt());
    private final PaymentTimedOut timedOut = new PaymentTimedOut(paymentId, timesOutAt);
    private final PaymentAuthorizationExpired authorizationExpired =
            new PaymentAuthorizationExpired(paymentId, captureBefore);

    private final Function<Payment, PaymentEvent[]> confirmAuthorization =
            current -> decide(new ConfirmPaymentAuthorization(paymentId, captureBefore, now), current);
    private final Function<Payment, PaymentEvent[]> requestCapture =
            current -> decide(new CapturePayment(paymentId, now), current);
    private final Function<Payment, PaymentEvent[]> confirmCapture =
            current -> decide(new ConfirmPaymentCapture(paymentId, now), current);
    private final Function<Payment, PaymentEvent[]> failCapture = current ->
            decide(new FailPaymentCapture(paymentId, Payment.CaptureFailureReason.UnexpectedError, now), current);
    private final Function<Payment, PaymentEvent[]> cancel =
            current -> decide(new CancelPayment(paymentId, now), current);
    private final Function<Payment, PaymentEvent[]> timeOut =
            current -> decide(new TimeOutPayment(paymentId, timesOutAt), current);
    private final Function<Payment, PaymentEvent[]> expireAuthorization =
            current -> decide(new ExpirePaymentAuthorization(paymentId, captureBefore), current);

    protected PaymentTests() {
        super(Payment::initial, Payment::evolve);
    }

    @Test
    public void requestingAuthorizationEmitsPaymentAuthorizationRequested() {
        // Given
        given()
                // When
                .when(current -> decide(new AuthorizePayment(paymentId, referenceId, amount, now, timesOutAt), current))
                // Then
                .then(authorizationRequested);
    }

    @Test
    public void requestingAuthorizationTwiceEmitsNothing() {
        // Given
        given(authorizationRequested)
                // When
                .when(current -> decide(new AuthorizePayment(paymentId, referenceId, amount, now, timesOutAt), current))
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingAPendingAuthorizationEmitsPaymentAuthorizedWithItsDeadline() {
        // Given
        given(authorizationRequested)
                // When
                .when(confirmAuthorization)
                // Then
                .then(authorized);
    }

    @Test
    public void requestingCaptureOfAnAuthorizedPaymentEmitsPaymentCaptureRequestedForTheAuthorizedAmount() {
        // Given
        given(authorizationRequested, authorized)
                // When
                .when(requestCapture)
                // Then
                .then(captureRequested);
    }

    @Test
    public void requestingCaptureOfAPaymentThatWasNeverAuthorizedEmitsNothing() {
        // Given
        given(authorizationRequested)
                // When
                .when(requestCapture)
                // Then
                .thenNothing();
    }

    @Test
    public void requestingCaptureTwiceEmitsNothing() {
        // Given
        given(authorizationRequested, authorized, captureRequested)
                // When
                .when(requestCapture)
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingARequestedCaptureEmitsPaymentCapturedForTheAuthorizedAmount() {
        // Given
        given(authorizationRequested, authorized, captureRequested)
                // When
                .when(confirmCapture)
                // Then
                .then(captured);
    }

    @Test
    public void confirmingACaptureThatWasNeverRequestedEmitsNothing() {
        // Given
        given(authorizationRequested, authorized)
                // When
                .when(confirmCapture)
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingACapturedPaymentEmitsNothing() {
        // Given
        given(authorizationRequested, authorized, captureRequested, captured)
                // When
                .when(confirmCapture)
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingAFailedCaptureEmitsNothing() {
        // Given
        given(authorizationRequested, authorized, captureRequested, captureFailed)
                // When
                .when(confirmCapture)
                // Then
                .thenNothing();
    }

    @Test
    public void failingARequestedCaptureEmitsPaymentCaptureFailedWithTheReason() {
        // Given
        given(authorizationRequested, authorized, captureRequested)
                // When
                .when(failCapture)
                // Then
                .then(captureFailed);
    }

    @Test
    public void failingACaptureThatWasNeverRequestedEmitsNothing() {
        // Given
        given(authorizationRequested, authorized)
                // When
                .when(failCapture)
                // Then
                .thenNothing();
    }

    @Test
    public void failingACapturedPaymentEmitsNothing() {
        // Given
        given(authorizationRequested, authorized, captureRequested, captured)
                // When
                .when(failCapture)
                // Then
                .thenNothing();
    }

    @Test
    public void cancelingAnAuthorizedPaymentEmitsPaymentCanceled() {
        // Given
        given(authorizationRequested, authorized)
                // When
                .when(cancel)
                // Then
                .then(canceled);
    }

    @Test
    public void voidingAPaymentWithARequestedCaptureEmitsNothingBecauseTheMoneyIsAlreadyMoving() {
        // Given
        given(authorizationRequested, authorized, captureRequested)
                // When
                .when(cancel)
                // Then
                .thenNothing();
    }

    @Test
    public void voidingACapturedPaymentEmitsNothingBecauseTheMoneyAlreadyMoved() {
        // Given
        given(authorizationRequested, authorized, captureRequested, captured)
                // When
                .when(cancel)
                // Then
                .thenNothing();
    }

    @Test
    public void recordingACheckoutSessionForAPendingPaymentEmitsCheckoutSessionCreated() {
        // Given
        given(authorizationRequested)
                // When
                .when(current -> decide(new RecordCheckoutSession(paymentId, checkoutSession, now), current))
                // Then
                .then(checkoutSessionCreated);
    }

    @Test
    public void recordingAnotherSessionWhileOneIsOpenEmitsNothing() {
        // Given
        given(authorizationRequested, checkoutSessionCreated)
                // When
                .when(current ->
                        decide(new RecordCheckoutSession(paymentId, nextCheckoutSession, now.plusMinutes(29)), current))
                // Then
                .thenNothing();
    }

    @Test
    public void recordingASessionAfterTheOpenOneExpiredEmitsCheckoutSessionCreatedForTheSamePayment() {
        // Given
        given(authorizationRequested, checkoutSessionCreated)
                // When
                .when(current -> decide(
                        new RecordCheckoutSession(paymentId, nextCheckoutSession, checkoutSession.expiresAt()),
                        current))
                // Then
                .then(new CheckoutSessionCreated(
                        paymentId, nextCheckoutSession.url(), nextCheckoutSession.expiresAt()));
    }

    @Test
    public void recordingASessionForAnAuthorizedPaymentEmitsNothing() {
        // Given
        given(authorizationRequested, authorized)
                // When
                .when(current -> decide(new RecordCheckoutSession(paymentId, checkoutSession, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void recordingASessionForATimedOutPaymentEmitsNothing() {
        // Given
        given(authorizationRequested, timedOut)
                // When
                .when(current -> decide(new RecordCheckoutSession(paymentId, checkoutSession, now), current))
                // Then
                .thenNothing();
    }

    @Test
    public void timingOutAPendingAuthorizationEmitsPaymentTimedOut() {
        // Given
        given(authorizationRequested)
                // When
                .when(timeOut)
                // Then
                .then(timedOut);
    }

    @Test
    public void timingOutBeforeItsDeadlineEmitsNothing() {
        // Given
        given(authorizationRequested)
                // When
                .when(current -> decide(new TimeOutPayment(paymentId, timesOutAt.minusMinutes(1)), current))
                // Then
                .thenNothing();
    }

    @Test
    public void expiringAnAuthorizedPaymentEmitsPaymentAuthorizationExpired() {
        // Given
        given(authorizationRequested, authorized)
                // When
                .when(expireAuthorization)
                // Then
                .then(authorizationExpired);
    }

    @Test
    public void expiringBeforeItsDeadlineEmitsNothing() {
        // Given
        given(authorizationRequested, authorized)
                // When
                .when(current ->
                        decide(new ExpirePaymentAuthorization(paymentId, captureBefore.minusMinutes(1)), current))
                // Then
                .thenNothing();
    }

    @Test
    public void expiringAnAuthorizationThatWasNeverGrantedEmitsNothing() {
        // Given
        given(authorizationRequested)
                // When
                .when(expireAuthorization)
                // Then
                .thenNothing();
    }

    @Test
    public void expiringAPaymentWithARequestedCaptureEmitsNothing() {
        // Given
        given(authorizationRequested, authorized, captureRequested)
                // When
                .when(expireAuthorization)
                // Then
                .thenNothing();
    }

    @Test
    public void confirmingAnAuthorizedPaymentEmitsNothing() {
        settlingAgainEmitsNothing(authorized, confirmAuthorization);
    }

    @Test
    public void confirmingATimedOutPaymentEmitsNothing() {
        settlingAgainEmitsNothing(timedOut, confirmAuthorization);
    }

    @Test
    public void timingOutAnAuthorizedPaymentEmitsNothing() {
        settlingAgainEmitsNothing(authorized, timeOut);
    }

    @Test
    public void timingOutATimedOutPaymentEmitsNothing() {
        settlingAgainEmitsNothing(timedOut, timeOut);
    }

    @Test
    public void requestingCaptureOfACanceledPaymentEmitsNothing() {
        // Given
        given(authorizationRequested, authorized, canceled)
                // When
                .when(requestCapture)
                // Then
                .thenNothing();
    }

    @Test
    public void requestingCaptureOfAnExpiredAuthorizationEmitsNothing() {
        // Given
        given(authorizationRequested, authorized, authorizationExpired)
                // When
                .when(requestCapture)
                // Then
                .thenNothing();
    }

    private void settlingAgainEmitsNothing(PaymentEvent settledEvent, Function<Payment, PaymentEvent[]> settleAgain) {
        // Given
        given(authorizationRequested, settledEvent)
                // When
                .when(settleAgain)
                // Then
                .thenNothing();
    }
}
