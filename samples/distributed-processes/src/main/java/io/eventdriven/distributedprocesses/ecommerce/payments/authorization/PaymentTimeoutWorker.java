package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;


import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.TimeOutPayment;

public class PaymentTimeoutWorker {
  private final PendingPayments pendingPayments;
  private final MessageBus messageBus;
  private final Duration timeout;

  public PaymentTimeoutWorker(
    PendingPayments pendingPayments,
    MessageBus messageBus,
    Duration timeout
  ) {
    this.pendingPayments = pendingPayments;
    this.messageBus = messageBus;
    this.timeout = timeout;
  }

  // Production would call this on a schedule; tests call it directly to keep time explicit.
  public void run(OffsetDateTime now) {
    for (var paymentId : pendingPayments.olderThan(now.minus(timeout))) {
      messageBus.publish(List.of(new TimeOutPayment(paymentId, now)));
    }
  }
}
