package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.core.messaging.EventEnvelope;
import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;

import java.time.OffsetDateTime;
import java.util.List;


import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.*;

public class PaymentExternalEventForwarder {
  private final PaymentSummaries summaries;
  private final MessageBus messageBus;

  public PaymentExternalEventForwarder(
    PaymentSummaries summaries,
    MessageBus messageBus
  ) {
    this.summaries = summaries;
    this.messageBus = messageBus;
  }

  public void onAuthorized(EventEnvelope<PaymentEvent.PaymentAuthorized> envelope) {
    var event = envelope.data();

    messageBus.publish(List.of(new PaymentExternalEvent.PaymentAuthorized(
      referenceIdOf(event.paymentId()),
      event.paymentId(),
      event.authorizedAt()
    )));
  }

  public void onCaptured(EventEnvelope<PaymentEvent.PaymentCaptured> envelope) {
    var event = envelope.data();

    messageBus.publish(List.of(new PaymentExternalEvent.PaymentCaptured(
      referenceIdOf(event.paymentId()),
      event.paymentId(),
      event.capturedAt()
    )));
  }

  public void onDeclined(EventEnvelope<PaymentDeclined> envelope) {
    publishFailure(envelope.data().paymentId(), envelope.data().declinedAt());
  }

  public void onTimedOut(EventEnvelope<PaymentTimedOut> envelope) {
    publishFailure(envelope.data().paymentId(), envelope.data().timedOutAt());
  }

  public void onAuthorizationExpired(EventEnvelope<PaymentAuthorizationExpired> envelope) {
    publishFailure(envelope.data().paymentId(), envelope.data().expiredAt());
  }

  public void onCaptureFailed(EventEnvelope<PaymentCaptureFailed> envelope) {
    publishFailure(envelope.data().paymentId(), envelope.data().failedAt());
  }

  private void publishFailure(PaymentId paymentId, OffsetDateTime failedAt) {
    messageBus.publish(List.of(new PaymentFailed(
      referenceIdOf(paymentId),
      paymentId,
      failedAt
    )));
  }

  private String referenceIdOf(PaymentId paymentId) {
    return summaries.get(paymentId)
      .orElseThrow(() -> new IllegalStateException(
        "Payment '%s' has no summary yet".formatted(paymentId.value())
      ))
      .getReferenceId();
  }
}
