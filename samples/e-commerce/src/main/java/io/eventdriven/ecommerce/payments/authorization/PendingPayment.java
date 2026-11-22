package io.eventdriven.ecommerce.payments.authorization;

import io.eventdriven.core.projections.Versioned;
import io.eventdriven.ecommerce.payments.Payment;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.time.OffsetDateTime;

@Entity
public class PendingPayment implements Versioned {
    @EmbeddedId
    private Payment.Id paymentId;

    private OffsetDateTime timesOutAt;
    private long version;

    public PendingPayment() {}

    public PendingPayment(Payment.Id paymentId, OffsetDateTime timesOutAt) {
        this.paymentId = paymentId;
        this.timesOutAt = timesOutAt;
    }

    public Payment.Id getPaymentId() {
        return paymentId;
    }

    public OffsetDateTime getTimesOutAt() {
        return timesOutAt;
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
