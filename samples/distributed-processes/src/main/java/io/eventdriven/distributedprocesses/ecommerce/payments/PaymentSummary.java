package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.projections.Versioned;

import java.time.OffsetDateTime;

public class PaymentSummary implements Versioned {
  public enum Status {
    Pending,
    Authorized,
    Captured,
    Voided,
    Refunded,
    Failed
  }

  private PaymentId paymentId;
  private String referenceId;
  private double amount;
  private Status status;
  private OffsetDateTime authorizedAt;
  private OffsetDateTime expiresAt;
  private long version;

  public PaymentSummary() {
  }

  public PaymentSummary(PaymentId paymentId, String referenceId, double amount, Status status) {
    this.paymentId = paymentId;
    this.referenceId = referenceId;
    this.amount = amount;
    this.status = status;
  }

  public PaymentId getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(PaymentId paymentId) {
    this.paymentId = paymentId;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  public double getAmount() {
    return amount;
  }

  public void setAmount(double amount) {
    this.amount = amount;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public OffsetDateTime getAuthorizedAt() {
    return authorizedAt;
  }

  public void setAuthorizedAt(OffsetDateTime authorizedAt) {
    this.authorizedAt = authorizedAt;
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
