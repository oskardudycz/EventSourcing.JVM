package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.core.projections.Projection;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

// What the forwarder adds to the internal events, kept where it can read it without loading
// the aggregate.
public class PaymentSummaries extends Projection<PaymentSummary> {
  public PaymentSummaries(Database database) {
    super(PaymentSummary.class, database);
  }

  @Override
  public List<Class<?>> canHandle() {
    return List.of(PaymentAuthorizationRequested.class);
  }

  @Override
  protected EntityId getDocumentId(Object event) {
    return switch (event) {
      case PaymentAuthorizationRequested e -> e.paymentId();
      default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
    };
  }

  @Override
  protected @Nullable PaymentSummary evolve(@Nullable PaymentSummary current, Object event) {
    return switch (event) {
      case PaymentAuthorizationRequested e -> new PaymentSummary(e.referenceId());
      default -> current;
    };
  }
}
