package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;

import java.time.OffsetDateTime;

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
    Captured,
    Voided,
    Refunded,
    Failed
  }

  private String referenceId;
  private double amount;
  private Status status;

  private Payment() {
  }

  public static Payment empty() {
    return new Payment();
  }

  public void requestAuthorization(PaymentId paymentId, String referenceId, double amount) {
    if (status != null)
      return;

    enqueue(new PaymentAuthorizationRequested(paymentId, referenceId, amount));
  }

  public void confirmAuthorization(OffsetDateTime now, OffsetDateTime expiresAt) {
    if (status != Status.Pending)
      return;

    enqueue(new PaymentAuthorized(id(), now, expiresAt));
  }

  public void capture(OffsetDateTime now) {
    if (status != Status.Authorized)
      return;

    enqueue(new PaymentCaptured(id(), amount, now));
  }

  public void voidAuthorization(OffsetDateTime now) {
    if (status != Status.Authorized)
      return;

    enqueue(new PaymentVoided(id(), now));
  }

  public void refund(OffsetDateTime now) {
    if (status != Status.Captured)
      return;

    enqueue(new PaymentRefunded(id(), now));
  }

  public void decline(DeclineReason reason, OffsetDateTime now) {
    if (status != Status.Pending)
      return;

    enqueue(new PaymentDeclined(id(), reason, now));
  }

  public void timeOut(OffsetDateTime now) {
    if (status != Status.Pending)
      return;

    enqueue(new PaymentTimedOut(id(), now));
  }

  public void expireAuthorization(OffsetDateTime now) {
    if (status != Status.Authorized)
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
        status = Status.Pending;
      }
      case PaymentAuthorized authorized -> status = Status.Authorized;
      case PaymentCaptured captured -> status = Status.Captured;
      case PaymentVoided voided -> status = Status.Voided;
      case PaymentRefunded refunded -> status = Status.Refunded;
      case PaymentDeclined declined -> status = Status.Failed;
      case PaymentTimedOut timedOut -> status = Status.Failed;
      case PaymentAuthorizationExpired expired -> status = Status.Failed;
    }
  }
}
