package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.core.projections.Versioned;

public class PaymentSummary implements Versioned {
  private String referenceId;
  private long version;

  public PaymentSummary() {
  }

  public PaymentSummary(String referenceId) {
    this.referenceId = referenceId;
  }

  public String getReferenceId() {
    return referenceId;
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
