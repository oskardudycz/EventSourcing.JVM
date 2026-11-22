package io.eventdriven.ecommerce.payments.authorization;

import static io.eventdriven.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.core.projections.Projection;
import io.eventdriven.ecommerce.payments.Payment;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class AuthorizedPayments extends Projection<AuthorizedPayment, Payment.Id> {
    private final AuthorizedPaymentRepository repository;

    public AuthorizedPayments(AuthorizedPaymentRepository repository) {
        super(repository);
        this.repository = repository;
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
    protected Payment.Id getDocumentId(Object event) {
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

    public List<Payment.Id> expiredAt(OffsetDateTime now) {
        return repository.findByCaptureBeforeLessThanEqual(now).stream()
                .map(AuthorizedPayment::getPaymentId)
                .toList();
    }
}
