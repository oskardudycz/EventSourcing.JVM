package io.eventdriven.distributedprocesses.ecommerce.payments;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class Payment extends AbstractAggregate<PaymentEvent, UUID> {
  public UUID orderId() {
    return orderId;
  }

  public double amount() {
    return amount;
  }

  private enum Status {
    Pending,
    Completed,
    Failed
  }

  private UUID orderId;
  private double amount;
  private Status status;

  public static Payment request(UUID paymentId, UUID orderId, double amount) {
    return new Payment(paymentId, orderId, amount);
  }

  private Payment(UUID id, UUID orderId, double amount) {
    enqueue(new PaymentRequested(id, orderId, amount));
  }

  public void complete(OffsetDateTime now) {
    if (status != Status.Pending)
      throw new IllegalStateException(
          "Completing payment in '%s' status is not allowed.".formatted(status));

    enqueue(new PaymentCompleted(id(), now));
  }

  public void discard(DiscardReason discardReason, OffsetDateTime now) {
    if (status != Status.Pending)
      throw new IllegalStateException(
          "Discarding payment in '{%s}' status is not allowed.".formatted(status));

    enqueue(new PaymentDiscarded(id(), discardReason, now));
  }

  public void timeOut(OffsetDateTime now) {
    if (status != Status.Pending)
      throw new IllegalStateException(
          "Discarding payment in '{%s}' status is not allowed.".formatted(status));

    var event = new PaymentTimedOut(id(), now);

    enqueue(event);
  }

  @Override
  public void when(PaymentEvent event) {
    switch (event) {
      case PaymentRequested paymentRequested -> {
        id = paymentRequested.paymentId();
        orderId = paymentRequested.orderId();
        amount = paymentRequested.amount();
        status = Status.Pending;
      }
      case PaymentCompleted completed -> status = Status.Completed;
      case PaymentDiscarded discarded -> status = Status.Failed;
      case PaymentTimedOut paymentTimedOut -> status = Status.Failed;
    }
  }
}
