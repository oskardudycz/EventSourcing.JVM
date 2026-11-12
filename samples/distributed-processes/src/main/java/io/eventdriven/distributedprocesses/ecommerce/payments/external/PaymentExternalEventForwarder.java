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
    var payment = get(event.paymentId(), envelope.metadata().streamPosition());

    messageBus.publish(List.of(new PaymentExternalEvent.PaymentAuthorized(
      payment.getReferenceId(),
      event.paymentId(),
      payment.getAmount(),
      payment.getAuthorizedAt(),
      payment.getExpiresAt()
    )));
  }

  public void onCaptured(EventEnvelope<PaymentEvent.PaymentCaptured> envelope) {
    var event = envelope.data();
    var payment = get(event.paymentId(), envelope.metadata().streamPosition());

    messageBus.publish(List.of(new PaymentExternalEvent.PaymentCaptured(
      payment.getReferenceId(),
      event.paymentId(),
      event.amount(),
      event.capturedAt()
    )));
  }

  public void onDeclined(EventEnvelope<PaymentDeclined> envelope) {
    var event = envelope.data();
    publishFailure(
      event.paymentId(),
      envelope.metadata().streamPosition(),
      event.declinedAt(),
      PaymentFailed.Reason.Declined
    );
  }

  public void onTimedOut(EventEnvelope<PaymentTimedOut> envelope) {
    var event = envelope.data();
    publishFailure(
      event.paymentId(),
      envelope.metadata().streamPosition(),
      event.timedOutAt(),
      PaymentFailed.Reason.TimedOut
    );
  }

  public void onAuthorizationExpired(EventEnvelope<PaymentAuthorizationExpired> envelope) {
    var event = envelope.data();
    publishFailure(
      event.paymentId(),
      envelope.metadata().streamPosition(),
      event.expiredAt(),
      PaymentFailed.Reason.AuthorizationExpired
    );
  }

  private void publishFailure(
    PaymentId paymentId,
    long streamPosition,
    OffsetDateTime failedAt,
    PaymentFailed.Reason reason
  ) {
    var payment = get(paymentId, streamPosition);

    messageBus.publish(List.of(new PaymentFailed(
      payment.getReferenceId(),
      paymentId,
      payment.getAmount(),
      failedAt,
      reason
    )));
  }

  private PaymentSummary get(PaymentId paymentId, long streamPosition) {
    return summaries.get(paymentId)
      .filter(summary -> summary.getVersion() >= streamPosition)
      .orElseThrow(() -> new IllegalStateException(
        "Summary of payment '%s' has not reached position %d yet".formatted(paymentId.value(), streamPosition)
      ));
  }
}
