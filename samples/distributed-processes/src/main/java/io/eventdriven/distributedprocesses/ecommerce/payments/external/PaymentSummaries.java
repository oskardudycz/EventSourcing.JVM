package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import java.util.List;
import org.jspecify.annotations.Nullable;

// What the forwarder adds to the internal events, kept where it can read it without rebuilding
// the payment from its stream.
public class PaymentSummaries extends Projection<PaymentSummary, PaymentId> {
    public PaymentSummaries(PaymentSummaryRepository repository) {
        super(repository);
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(PaymentAuthorizationRequested.class);
    }

    @Override
    protected PaymentId getDocumentId(Object event) {
        return switch (event) {
            case PaymentAuthorizationRequested e -> e.paymentId();
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
        };
    }

    @Override
    protected @Nullable PaymentSummary evolve(@Nullable PaymentSummary current, Object event) {
        return switch (event) {
            case PaymentAuthorizationRequested e -> new PaymentSummary(e.paymentId(), e.referenceId());
            default -> current;
        };
    }
}
