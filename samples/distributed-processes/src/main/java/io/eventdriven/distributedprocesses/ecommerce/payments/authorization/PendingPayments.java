package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.projections.Database;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public class PendingPayments {
  private final Database database;
  private final Supplier<OffsetDateTime> now;

  public PendingPayments(Database database, Supplier<OffsetDateTime> now) {
    this.database = database;
    this.now = now;
  }

  public void onAuthorizationRequested(EventEnvelope<PaymentAuthorizationRequested> event) {
    database.store(
      PendingPayment.class,
      event.data().paymentId(),
      event.metadata().streamPosition(),
      new PendingPayment(event.data().paymentId(), now.get())
    );
  }

  public void onAuthorized(EventEnvelope<PaymentAuthorized> event) {
    database.delete(PendingPayment.class, event.data().paymentId());
  }

  public void onDeclined(EventEnvelope<PaymentDeclined> event) {
    database.delete(PendingPayment.class, event.data().paymentId());
  }

  public void onTimedOut(EventEnvelope<PaymentTimedOut> event) {
    database.delete(PendingPayment.class, event.data().paymentId());
  }

  public List<PaymentId> olderThan(OffsetDateTime threshold) {
    return database
      .find(PendingPayment.class, pending -> pending.getRequestedAt().isBefore(threshold))
      .stream()
      .map(PendingPayment::getPaymentId)
      .toList();
  }
}
