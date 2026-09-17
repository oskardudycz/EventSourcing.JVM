package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.projections.Versioned;

import java.time.OffsetDateTime;

public class AuthorizedPayment implements Versioned {
  private PaymentId paymentId;
  private OffsetDateTime expiresAt;
  private long version;

  public AuthorizedPayment() {
  }

  public AuthorizedPayment(PaymentId paymentId, OffsetDateTime expiresAt) {
    this.paymentId = paymentId;
    this.expiresAt = expiresAt;
  }

  public PaymentId getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(PaymentId paymentId) {
    this.paymentId = paymentId;
  }

  public OffsetDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(OffsetDateTime expiresAt) {
    this.expiresAt = expiresAt;
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
