package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

@Entity
public class PaymentSummary implements Versioned {
    @EmbeddedId
    private PaymentId paymentId;

    private String referenceId;
    private long version;

    public PaymentSummary() {}

    public PaymentSummary(PaymentId paymentId, String referenceId) {
        this.paymentId = paymentId;
        this.referenceId = referenceId;
    }

    public PaymentId getPaymentId() {
        return paymentId;
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
