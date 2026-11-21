package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import java.time.OffsetDateTime;
import java.util.List;
import org.jspecify.annotations.Nullable;

public class PendingPayments extends Projection<PendingPayment, PaymentId> {
    private final PendingPaymentRepository repository;

    public PendingPayments(PendingPaymentRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(PaymentAuthorizationRequested.class, PaymentAuthorized.class, PaymentTimedOut.class);
    }

    @Override
    protected PaymentId getDocumentId(Object event) {
        return switch (event) {
            case PaymentAuthorizationRequested e -> e.paymentId();
            case PaymentAuthorized e -> e.paymentId();
            case PaymentTimedOut e -> e.paymentId();
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
        };
    }

    @Override
    protected @Nullable PendingPayment evolve(@Nullable PendingPayment current, Object event) {
        return switch (event) {
            case PaymentAuthorizationRequested e -> new PendingPayment(e.paymentId(), e.timesOutAt());
            case PaymentAuthorized _, PaymentTimedOut _ -> null;
            default -> current;
        };
    }

    public List<PaymentId> timedOutAt(OffsetDateTime now) {
        return repository.findByTimesOutAtLessThanEqual(now).stream()
                .map(PendingPayment::getPaymentId)
                .toList();
    }
}
