package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;

import java.time.OffsetDateTime;
import java.util.Optional;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public class Payment extends AbstractAggregate<PaymentEvent, PaymentId> {
  public String referenceId() {
    return referenceId;
  }

  public double amount() {
    return amount;
  }

  private enum Status {
    Pending,
    Authorized,
    CaptureRequested,
    Captured,
    Canceled,
    Failed
  }

  private String referenceId;
  private double amount;
  private Status status;
  private OffsetDateTime timesOutAt;
  private OffsetDateTime captureBefore;
  private CheckoutSession checkoutSession;

  private Payment() {
  }

  public static Payment empty() {
    return new Payment();
  }

  public boolean isPending() {
    return status == Status.Pending;
  }

  public Optional<CheckoutSession> openCheckoutSession(OffsetDateTime now) {
    return checkoutSession != null && now.isBefore(checkoutSession.expiresAt())
      ? Optional.of(checkoutSession)
      : Optional.empty();
  }

  public void requestAuthorization(
    PaymentId paymentId,
    String referenceId,
    double amount,
    OffsetDateTime now,
    OffsetDateTime timesOutAt
  ) {
    if (status != null)
      return;

    enqueue(new PaymentAuthorizationRequested(paymentId, referenceId, amount, now, timesOutAt));
  }

  public void recordCheckoutSession(CheckoutSession session, OffsetDateTime now) {
    if (status != Status.Pending || openCheckoutSession(now).isPresent())
      return;

    enqueue(new CheckoutSessionCreated(id(), session.url(), session.expiresAt()));
  }

  public void confirmAuthorization(OffsetDateTime now, OffsetDateTime captureBefore) {
    if (status != Status.Pending)
      return;

    enqueue(new PaymentAuthorized(id(), now, captureBefore));
  }

  public void requestCapture(OffsetDateTime now) {
    if (status != Status.Authorized)
      return;

    enqueue(new PaymentCaptureRequested(id(), amount, now));
  }

  public void confirmCapture(OffsetDateTime now) {
    if (status != Status.CaptureRequested)
      return;

    enqueue(new PaymentCaptured(id(), amount, now));
  }

  public void failCapture(CaptureFailureReason reason, OffsetDateTime now) {
    if (status != Status.CaptureRequested)
      return;

    enqueue(new PaymentCaptureFailed(id(), reason, now));
  }

  public void cancel(OffsetDateTime now) {
    if (status != Status.Authorized)
      return;

    enqueue(new PaymentCanceled(id(), now));
  }


  public void timeOut(OffsetDateTime now) {
    if (status != Status.Pending || now.isBefore(timesOutAt))
      return;

    enqueue(new PaymentTimedOut(id(), now));
  }

  public void expireAuthorization(OffsetDateTime now) {
    if (status != Status.Authorized || now.isBefore(captureBefore))
      return;

    enqueue(new PaymentAuthorizationExpired(id(), now));
  }

  @Override
  public void evolve(PaymentEvent event) {
    switch (event) {
      case PaymentAuthorizationRequested authorizationRequested -> {
        id = authorizationRequested.paymentId();
        referenceId = authorizationRequested.referenceId();
        amount = authorizationRequested.amount();
        timesOutAt = authorizationRequested.timesOutAt();
        status = Status.Pending;
      }
      case CheckoutSessionCreated sessionCreated ->
        checkoutSession = new CheckoutSession(sessionCreated.url(), sessionCreated.expiresAt());
      case PaymentAuthorized authorized -> {
        captureBefore = authorized.captureBefore();
        status = Status.Authorized;
      }
      case PaymentCaptureRequested captureRequested -> status = Status.CaptureRequested;
      case PaymentCaptured captured -> status = Status.Captured;
      case PaymentCaptureFailed captureFailed -> status = Status.Failed;
      case PaymentCanceled canceled -> status = Status.Canceled;
      case PaymentTimedOut timedOut -> status = Status.Failed;
      case PaymentAuthorizationExpired expired -> status = Status.Failed;
    }
  }
}
