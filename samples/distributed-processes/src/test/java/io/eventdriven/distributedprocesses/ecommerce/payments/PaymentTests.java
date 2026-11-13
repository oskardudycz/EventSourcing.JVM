package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.identifiers.Urns;

import io.eventdriven.testing.AggregateSpecification;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public class PaymentTests extends AggregateSpecification<Payment, PaymentEvent, PaymentId> {
  private final PaymentId paymentId = PaymentId.of(UUID.randomUUID());
  private final String referenceId = Urns.of("ecommerce", "order", UUID.randomUUID());
  private final double amount = 62.5;
  private final OffsetDateTime now = OffsetDateTime.now();
  private final OffsetDateTime expiresAt = now.plusDays(7);

  private final PaymentAuthorizationRequested authorizationRequested =
    new PaymentAuthorizationRequested(paymentId, referenceId, amount, now);
  private final PaymentAuthorized authorized = new PaymentAuthorized(paymentId, now, expiresAt);
  private final PaymentCaptureRequested captureRequested =
    new PaymentCaptureRequested(paymentId, amount, now);
  private final PaymentCaptured captured = new PaymentCaptured(paymentId, amount, now);
  private final PaymentCaptureFailed captureFailed =
    new PaymentCaptureFailed(paymentId, CaptureFailureReason.UnexpectedError, now);
  private final PaymentVoided voided = new PaymentVoided(paymentId, now);
  private final PaymentDeclined declined =
    new PaymentDeclined(paymentId, DeclineReason.UnexpectedError, now);
  private final PaymentTimedOut timedOut = new PaymentTimedOut(paymentId, now);
  private final PaymentAuthorizationExpired authorizationExpired =
    new PaymentAuthorizationExpired(paymentId, now);

  private final Consumer<Payment> confirmAuthorization =
    current -> current.confirmAuthorization(now, expiresAt);
  private final Consumer<Payment> requestCapture = current -> current.requestCapture(now);
  private final Consumer<Payment> confirmCapture = current -> current.confirmCapture(now);
  private final Consumer<Payment> failCapture =
    current -> current.failCapture(CaptureFailureReason.UnexpectedError, now);
  private final Consumer<Payment> voidAuthorization = current -> current.voidAuthorization(now);
  private final Consumer<Payment> decline =
    current -> current.decline(DeclineReason.UnexpectedError, now);
  private final Consumer<Payment> timeOut = current -> current.timeOut(now);
  private final Consumer<Payment> expireAuthorization = current -> current.expireAuthorization(now);

  protected PaymentTests() {
    super(Payment::empty);
  }

  @Test
  public void requestingAuthorizationEmitsPaymentAuthorizationRequested() {
    // Given
    given()
      // When
      .when(current -> current.requestAuthorization(paymentId, referenceId, amount, now))
      // Then
      .then(authorizationRequested);
  }

  @Test
  public void requestingAuthorizationTwiceEmitsNothing() {
    // Given
    given(authorizationRequested)
      // When
      .when(current -> current.requestAuthorization(paymentId, referenceId, amount, now))
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
  public void voidingAnAuthorizedPaymentEmitsPaymentVoided() {
    // Given
    given(authorizationRequested, authorized)
      // When
      .when(voidAuthorization)
      // Then
      .then(voided);
  }

  @Test
  public void voidingAPaymentWithARequestedCaptureEmitsNothingBecauseTheMoneyIsAlreadyMoving() {
    // Given
    given(authorizationRequested, authorized, captureRequested)
      // When
      .when(voidAuthorization)
      // Then
      .thenNothing();
  }

  @Test
  public void voidingACapturedPaymentEmitsNothingBecauseTheMoneyAlreadyMoved() {
    // Given
    given(authorizationRequested, authorized, captureRequested, captured)
      // When
      .when(voidAuthorization)
      // Then
      .thenNothing();
  }

  @Test
  public void decliningAPendingAuthorizationEmitsPaymentDeclinedWithTheReason() {
    // Given
    given(authorizationRequested)
      // When
      .when(decline)
      // Then
      .then(declined);
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
  public void expiringAnAuthorizedPaymentEmitsPaymentAuthorizationExpired() {
    // Given
    given(authorizationRequested, authorized)
      // When
      .when(expireAuthorization)
      // Then
      .then(authorizationExpired);
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
  public void confirmingADeclinedPaymentEmitsNothing() {
    settlingAgainEmitsNothing(declined, confirmAuthorization);
  }

  @Test
  public void confirmingATimedOutPaymentEmitsNothing() {
    settlingAgainEmitsNothing(timedOut, confirmAuthorization);
  }

  @Test
  public void decliningAnAuthorizedPaymentEmitsNothing() {
    settlingAgainEmitsNothing(authorized, decline);
  }

  @Test
  public void decliningADeclinedPaymentEmitsNothing() {
    settlingAgainEmitsNothing(declined, decline);
  }

  @Test
  public void decliningATimedOutPaymentEmitsNothing() {
    settlingAgainEmitsNothing(timedOut, decline);
  }

  @Test
  public void timingOutAnAuthorizedPaymentEmitsNothing() {
    settlingAgainEmitsNothing(authorized, timeOut);
  }

  @Test
  public void timingOutADeclinedPaymentEmitsNothing() {
    settlingAgainEmitsNothing(declined, timeOut);
  }

  @Test
  public void timingOutATimedOutPaymentEmitsNothing() {
    settlingAgainEmitsNothing(timedOut, timeOut);
  }

  @Test
  public void requestingCaptureOfAVoidedPaymentEmitsNothing() {
    // Given
    given(authorizationRequested, authorized, voided)
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

  private void settlingAgainEmitsNothing(PaymentEvent settledEvent, Consumer<Payment> settleAgain) {
    // Given
    given(authorizationRequested, settledEvent)
      // When
      .when(settleAgain)
      // Then
      .thenNothing();
  }
}
