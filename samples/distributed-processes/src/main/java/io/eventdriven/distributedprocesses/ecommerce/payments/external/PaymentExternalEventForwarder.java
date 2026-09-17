package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentSummaries;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentSummary;

import java.time.OffsetDateTime;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.*;

public class PaymentExternalEventForwarder {
  private final PaymentSummaries summaries;
  private final IntegrationEventBus eventBus;

  public PaymentExternalEventForwarder(
    PaymentSummaries summaries,
    IntegrationEventBus eventBus
  ) {
    this.summaries = summaries;
    this.eventBus = eventBus;
  }

  public void on(PaymentEvent.PaymentAuthorized event) {
    var payment = get(event.paymentId());

    eventBus.publish(new PaymentExternalEvent.PaymentAuthorized(
      payment.getReferenceId(),
      event.paymentId(),
      payment.getAmount(),
      payment.getAuthorizedAt(),
      payment.getExpiresAt()
    ));
  }

  public void on(PaymentEvent.PaymentCaptured event) {
    var payment = get(event.paymentId());

    eventBus.publish(new PaymentExternalEvent.PaymentCaptured(
      payment.getReferenceId(),
      event.paymentId(),
      event.amount(),
      event.capturedAt()
    ));
  }

  public void on(PaymentDeclined event) {
    publishFailure(event.paymentId(), event.declinedAt(), PaymentFailed.Reason.Declined);
  }

  public void on(PaymentTimedOut event) {
    publishFailure(event.paymentId(), event.timedOutAt(), PaymentFailed.Reason.TimedOut);
  }

  public void on(PaymentAuthorizationExpired event) {
    publishFailure(
      event.paymentId(),
      event.expiredAt(),
      PaymentFailed.Reason.AuthorizationExpired
    );
  }

  private void publishFailure(
    PaymentId paymentId,
    OffsetDateTime failedAt,
    PaymentFailed.Reason reason
  ) {
    var payment = get(paymentId);

    eventBus.publish(new PaymentFailed(
      payment.getReferenceId(),
      paymentId,
      payment.getAmount(),
      failedAt,
      reason
    ));
  }

  private PaymentSummary get(PaymentId paymentId) {
    return summaries.get(paymentId)
      .orElseThrow(() -> new IllegalStateException("Cannot enrich event, as payment with id '%s' was not found".formatted(paymentId)));
  }
}
