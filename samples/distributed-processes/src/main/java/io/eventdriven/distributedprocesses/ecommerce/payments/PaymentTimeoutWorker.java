package io.eventdriven.distributedprocesses.ecommerce.payments;

import io.eventdriven.distributedprocesses.core.messaging.CommandBus;

import java.time.Duration;
import java.time.OffsetDateTime;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.TimeOutPayment;

public class PaymentTimeoutWorker {
  private final PendingPayments pendingPayments;
  private final CommandBus commandBus;
  private final Duration timeout;

  public PaymentTimeoutWorker(
    PendingPayments pendingPayments,
    CommandBus commandBus,
    Duration timeout
  ) {
    this.pendingPayments = pendingPayments;
    this.commandBus = commandBus;
    this.timeout = timeout;
  }

  // Production would call this on a schedule; tests call it directly to keep time explicit.
  public void run(OffsetDateTime now) {
    for (var paymentId : pendingPayments.olderThan(now.minus(timeout))) {
      commandBus.send(new TimeOutPayment(paymentId, now));
    }
  }
}
