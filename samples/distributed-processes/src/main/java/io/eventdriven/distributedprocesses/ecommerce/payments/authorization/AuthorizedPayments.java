package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class AuthorizedPayments extends Projection<AuthorizedPayment> {
    public AuthorizedPayments(Database database) {
        super(AuthorizedPayment.class, database);
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(
                PaymentAuthorized.class,
                PaymentCaptureRequested.class,
                PaymentCanceled.class,
                PaymentAuthorizationExpired.class);
    }

    @Override
    protected EntityId getDocumentId(Object event) {
        return switch (event) {
            case PaymentAuthorized e -> e.paymentId();
            case PaymentCaptureRequested e -> e.paymentId();
            case PaymentCanceled e -> e.paymentId();
            case PaymentAuthorizationExpired e -> e.paymentId();
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
        };
    }

    @Override
    protected @Nullable AuthorizedPayment evolve(@Nullable AuthorizedPayment current, Object event) {
        return switch (event) {
            case PaymentAuthorized e -> new AuthorizedPayment(e.paymentId(), e.captureBefore());
            case PaymentCaptureRequested _, PaymentCanceled _, PaymentAuthorizationExpired _ -> null;
            default -> current;
        };
    }

    public List<PaymentId> expiredAt(OffsetDateTime now) {
        return find(authorized -> !authorized.getCaptureBefore().isAfter(now)).stream()
                .map(AuthorizedPayment::getPaymentId)
                .toList();
    }
}
