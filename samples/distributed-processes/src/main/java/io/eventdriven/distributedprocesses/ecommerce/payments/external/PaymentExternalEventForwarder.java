package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.messaging.IntegrationEventBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.Payment;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent;

import java.time.OffsetDateTime;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.*;

public class PaymentExternalEventForwarder {
  private final AggregateStore<Payment, PaymentEvent, PaymentId> store;
  private final IntegrationEventBus eventBus;

  public PaymentExternalEventForwarder(
    AggregateStore<Payment, PaymentEvent, PaymentId> store,
    IntegrationEventBus eventBus
  ) {
    this.store = store;
    this.eventBus = eventBus;
  }

  public void on(PaymentEvent.PaymentAuthorized event) {
    var payment = get(event.paymentId());

    eventBus.publish(new PaymentExternalEvent.PaymentAuthorized(
      payment.referenceId(),
      event.paymentId(),
      payment.amount(),
      event.authorizedAt(),
      event.expiresAt()
    ));
  }

  public void on(PaymentEvent.PaymentCaptured event) {
    var payment = get(event.paymentId());

    eventBus.publish(new PaymentExternalEvent.PaymentCaptured(
      payment.referenceId(),
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
      payment.referenceId(),
      paymentId,
      payment.amount(),
      failedAt,
      reason
    ));
  }

  private Payment get(PaymentId paymentId) {
    return store.get(paymentId)
      .orElseThrow(() -> new IllegalStateException("Cannot enrich event, as payment with id '%s' was not found".formatted(paymentId)));
  }
}
