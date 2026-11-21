package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.time.OffsetDateTime;

@Entity
public class AuthorizedPayment implements Versioned {
    @EmbeddedId
    private PaymentId paymentId;

    private OffsetDateTime captureBefore;
    private long version;

    public AuthorizedPayment() {}

    public AuthorizedPayment(PaymentId paymentId, OffsetDateTime captureBefore) {
        this.paymentId = paymentId;
        this.captureBefore = captureBefore;
    }

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public OffsetDateTime getCaptureBefore() {
        return captureBefore;
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
