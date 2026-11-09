package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;

import java.time.OffsetDateTime;

public class PendingPayment implements Versioned {
  private PaymentId paymentId;
  private OffsetDateTime requestedAt;
  private long version;

  public PendingPayment() {
  }

  public PendingPayment(PaymentId paymentId, OffsetDateTime requestedAt) {
    this.paymentId = paymentId;
    this.requestedAt = requestedAt;
  }

  public PaymentId getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(PaymentId paymentId) {
    this.paymentId = paymentId;
  }

  public OffsetDateTime getRequestedAt() {
    return requestedAt;
  }

  public void setRequestedAt(OffsetDateTime requestedAt) {
    this.requestedAt = requestedAt;
  }

  @Override
  public long getVersion() {
    return version;
  }

  @Override
  public void setVersion(long version) {
    this.version = version;
  }
}
