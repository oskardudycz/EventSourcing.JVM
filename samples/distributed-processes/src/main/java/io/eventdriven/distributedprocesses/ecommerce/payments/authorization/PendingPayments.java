package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public class PendingPayments extends Projection<PendingPayment> {
  public PendingPayments(Database database) {
    super(PendingPayment.class, database);
  }

  @Override
  public List<Class<?>> canHandle() {
    return List.of(
      PaymentAuthorizationRequested.class,
      PaymentAuthorized.class,
      PaymentDeclined.class,
      PaymentTimedOut.class
    );
  }

  @Override
  protected EntityId getDocumentId(Object event) {
    return switch (event) {
      case PaymentAuthorizationRequested e -> e.paymentId();
      case PaymentAuthorized e -> e.paymentId();
      case PaymentDeclined e -> e.paymentId();
      case PaymentTimedOut e -> e.paymentId();
      default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
    };
  }

  @Override
  protected @Nullable PendingPayment evolve(@Nullable PendingPayment current, Object event) {
    return switch (event) {
      case PaymentAuthorizationRequested e -> new PendingPayment(e.paymentId(), e.requestedAt());
      case PaymentAuthorized _, PaymentDeclined _, PaymentTimedOut _ -> null;
      default -> current;
    };
  }

  public List<PaymentId> olderThan(OffsetDateTime threshold) {
    return find(pending -> pending.getRequestedAt().isBefore(threshold)).stream()
      .map(PendingPayment::getPaymentId)
      .toList();
  }
}
