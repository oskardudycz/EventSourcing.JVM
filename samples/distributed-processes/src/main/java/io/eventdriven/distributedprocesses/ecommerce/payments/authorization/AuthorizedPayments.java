package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;

import java.time.OffsetDateTime;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public class AuthorizedPayments {
  private final Database database;

  public AuthorizedPayments(Database database) {
    this.database = database;
  }

  public void onAuthorized(EventEnvelope<PaymentAuthorized> event) {
    database.store(
      AuthorizedPayment.class,
      event.data().paymentId(),
      event.metadata().streamPosition(),
      new AuthorizedPayment(event.data().paymentId(), event.data().expiresAt())
    );
  }

  public void onCaptured(EventEnvelope<PaymentCaptured> event) {
    database.delete(AuthorizedPayment.class, event.data().paymentId());
  }

  public void onVoided(EventEnvelope<PaymentVoided> event) {
    database.delete(AuthorizedPayment.class, event.data().paymentId());
  }

  public void onAuthorizationExpired(EventEnvelope<PaymentAuthorizationExpired> event) {
    database.delete(AuthorizedPayment.class, event.data().paymentId());
  }

  public List<PaymentId> expiredAt(OffsetDateTime now) {
    return database
      .find(AuthorizedPayment.class, authorized -> !authorized.getExpiresAt().isAfter(now))
      .stream()
      .map(AuthorizedPayment::getPaymentId)
      .toList();
  }
}
