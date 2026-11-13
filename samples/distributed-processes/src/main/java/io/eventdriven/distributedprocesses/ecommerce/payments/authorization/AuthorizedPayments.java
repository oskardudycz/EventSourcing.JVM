package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.identifiers.EntityId;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public class AuthorizedPayments extends Projection<AuthorizedPayment> {
  public AuthorizedPayments(Database database) {
    super(AuthorizedPayment.class, database);
  }

  @Override
  public List<Class<?>> canHandle() {
    return List.of(
      PaymentAuthorized.class,
      PaymentCaptureRequested.class,
      PaymentVoided.class,
      PaymentAuthorizationExpired.class
    );
  }

  @Override
  protected EntityId getDocumentId(Object event) {
    return switch (event) {
      case PaymentAuthorized e -> e.paymentId();
      case PaymentCaptureRequested e -> e.paymentId();
      case PaymentVoided e -> e.paymentId();
      case PaymentAuthorizationExpired e -> e.paymentId();
      default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
    };
  }

  @Override
  protected @Nullable AuthorizedPayment evolve(@Nullable AuthorizedPayment current, Object event) {
    return switch (event) {
      case PaymentAuthorized e -> new AuthorizedPayment(e.paymentId(), e.expiresAt());
      case PaymentCaptureRequested _, PaymentVoided _, PaymentAuthorizationExpired _ -> null;
      default -> current;
    };
  }

  public List<PaymentId> expiredAt(OffsetDateTime now) {
    return find(authorized -> !authorized.getExpiresAt().isAfter(now)).stream()
      .map(AuthorizedPayment::getPaymentId)
      .toList();
  }
}
