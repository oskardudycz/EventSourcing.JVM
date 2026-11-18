package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import java.time.OffsetDateTime;

public class PendingPayment implements Versioned {
    private PaymentId paymentId;
    private OffsetDateTime timesOutAt;
    private long version;

    public PendingPayment() {}

    public PendingPayment(PaymentId paymentId, OffsetDateTime timesOutAt) {
        this.paymentId = paymentId;
        this.timesOutAt = timesOutAt;
    }

    public PaymentId getPaymentId() {
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
