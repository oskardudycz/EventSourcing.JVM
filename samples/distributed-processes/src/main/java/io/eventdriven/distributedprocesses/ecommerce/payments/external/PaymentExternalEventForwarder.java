package io.eventdriven.distributedprocesses.ecommerce.payments.external;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;
import static io.eventdriven.distributedprocesses.ecommerce.payments.external.PaymentExternalEvent.*;

import io.eventdriven.distributedprocesses.core.aggregates.AggregateStore;
import io.eventdriven.distributedprocesses.core.events.EventBus;
import io.eventdriven.distributedprocesses.ecommerce.payments.Payment;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent;
import java.util.UUID;

public class PaymentExternalEventForwarder {
  private final AggregateStore<Payment, PaymentEvent, UUID> store;
  private final EventBus eventBus;

  public PaymentExternalEventForwarder(
      AggregateStore<Payment, PaymentEvent, UUID> store, EventBus eventBus) {
    this.store = store;
    this.eventBus = eventBus;
  }

  public void on(PaymentCompleted event) {
    var payment =
        store
            .get(event.paymentId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cannot enrich event, as payment with id '%s' was not found"
                            .formatted(event.paymentId())));

    var externalEvent =
        new PaymentFinalized(
            payment.orderId(), event.paymentId(), payment.amount(), event.completedAt());

    eventBus.publish(externalEvent);
  }

  public void on(PaymentDiscarded event) {
    var payment =
        store
            .get(event.paymentId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cannot enrich event, as payment with id '%s' was not found"
                            .formatted(event.paymentId())));

    var externalEvent =
        new PaymentFailed(
            payment.orderId(),
            event.paymentId(),
            payment.amount(),
            event.discardedAt(),
            PaymentFailed.Reason.Discarded);

    eventBus.publish(externalEvent);
  }

  public void on(PaymentTimedOut event) {
    var payment =
        store
            .get(event.paymentId())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Cannot enrich event, as payment with id '%s' was not found"
                            .formatted(event.paymentId())));

    var externalEvent =
        new PaymentFailed(
            payment.orderId(),
            event.paymentId(),
            payment.amount(),
            event.timedOutAt(),
            PaymentFailed.Reason.TimedOut);

    eventBus.publish(externalEvent);
  }
}
