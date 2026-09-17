package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.projections.Database;

import java.util.Optional;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

// Everything the outside world is told about a payment, kept where the forwarder can read it
// without loading the aggregate. Handlers are named per event, as EventEnvelope<PaymentAuthorized>
// and EventEnvelope<PaymentCaptured> erase to the same overload.
public class PaymentSummaries {
  private final Database database;

  public PaymentSummaries(Database database) {
    this.database = database;
  }

  public Optional<PaymentSummary> get(PaymentId paymentId) {
    return database.get(PaymentSummary.class, paymentId);
  }

  public void onAuthorizationRequested(EventEnvelope<PaymentAuthorizationRequested> event) {
    database.store(
      PaymentSummary.class,
      event.data().paymentId(),
      event.metadata().streamPosition(),
      new PaymentSummary(
        event.data().paymentId(),
        event.data().referenceId(),
        event.data().amount(),
        PaymentSummary.Status.Pending
      )
    );
  }

  public void onAuthorized(EventEnvelope<PaymentAuthorized> event) {
    database.getAndUpdate(
      PaymentSummary.class,
      event.data().paymentId(),
      event.metadata().streamPosition(),
      summary -> {
        summary.setStatus(PaymentSummary.Status.Authorized);
        summary.setAuthorizedAt(event.data().authorizedAt());
        summary.setExpiresAt(event.data().expiresAt());

        return summary;
      }
    );
  }

  public void onCaptured(EventEnvelope<PaymentCaptured> event) {
    changeStatus(event.data().paymentId(), event, PaymentSummary.Status.Captured);
  }

  public void onVoided(EventEnvelope<PaymentVoided> event) {
    changeStatus(event.data().paymentId(), event, PaymentSummary.Status.Voided);
  }

  public void onRefunded(EventEnvelope<PaymentRefunded> event) {
    changeStatus(event.data().paymentId(), event, PaymentSummary.Status.Refunded);
  }

  public void onDeclined(EventEnvelope<PaymentDeclined> event) {
    changeStatus(event.data().paymentId(), event, PaymentSummary.Status.Failed);
  }

  public void onTimedOut(EventEnvelope<PaymentTimedOut> event) {
    changeStatus(event.data().paymentId(), event, PaymentSummary.Status.Failed);
  }

  public void onAuthorizationExpired(EventEnvelope<PaymentAuthorizationExpired> event) {
    changeStatus(event.data().paymentId(), event, PaymentSummary.Status.Failed);
  }

  private void changeStatus(
    PaymentId paymentId,
    EventEnvelope<?> event,
    PaymentSummary.Status status
  ) {
    database.getAndUpdate(
      PaymentSummary.class,
      paymentId,
      event.metadata().streamPosition(),
      summary -> {
        summary.setStatus(status);

        return summary;
      }
    );
  }
}
