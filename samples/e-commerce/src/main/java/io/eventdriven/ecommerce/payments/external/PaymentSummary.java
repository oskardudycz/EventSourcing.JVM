package io.eventdriven.ecommerce.payments.external;

import io.eventdriven.core.projections.Versioned;
import io.eventdriven.ecommerce.payments.Payment;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;

@Entity
public class PaymentSummary implements Versioned {
    @EmbeddedId
    private Payment.Id paymentId;

    private String referenceId;
    private long version;

    public PaymentSummary() {}

    public PaymentSummary(Payment.Id paymentId, String referenceId) {
        this.paymentId = paymentId;
        this.referenceId = referenceId;
    }

    public Payment.Id getPaymentId() {
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
