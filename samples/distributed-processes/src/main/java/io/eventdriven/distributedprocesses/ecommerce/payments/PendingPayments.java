package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public class PendingPayments {
  private final Map<PaymentId, OffsetDateTime> requestedAt = new HashMap<>();
  private final Supplier<OffsetDateTime> now;

  public PendingPayments(Supplier<OffsetDateTime> now) {
    this.now = now;
  }

  public void on(PaymentAuthorizationRequested event) {
    requestedAt.put(event.paymentId(), now.get());
  }

  public void on(PaymentAuthorized event) {
    requestedAt.remove(event.paymentId());
  }

  public void on(PaymentDeclined event) {
    requestedAt.remove(event.paymentId());
  }

  public void on(PaymentTimedOut event) {
    requestedAt.remove(event.paymentId());
  }

  public List<PaymentId> olderThan(OffsetDateTime threshold) {
    return requestedAt.entrySet().stream()
      .filter(entry -> entry.getValue().isBefore(threshold))
      .map(Map.Entry::getKey)
      .toList();
  }
}
