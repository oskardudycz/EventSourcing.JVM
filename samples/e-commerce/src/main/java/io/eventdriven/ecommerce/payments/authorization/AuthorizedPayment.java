package io.eventdriven.ecommerce.payments.authorization;

import io.eventdriven.core.projections.Versioned;
import io.eventdriven.ecommerce.payments.Payment;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import java.time.OffsetDateTime;

@Entity
public class AuthorizedPayment implements Versioned {
    @EmbeddedId
    private Payment.Id paymentId;

    private OffsetDateTime captureBefore;
    private long version;

    public AuthorizedPayment() {}

    public AuthorizedPayment(Payment.Id paymentId, OffsetDateTime captureBefore) {
        this.paymentId = paymentId;
        this.captureBefore = captureBefore;
    }

    public Payment.Id getPaymentId() {
        return paymentId;
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
