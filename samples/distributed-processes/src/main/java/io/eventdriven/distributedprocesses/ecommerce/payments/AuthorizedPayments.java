package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentEvent.*;

public class AuthorizedPayments {
  private final Map<PaymentId, OffsetDateTime> expiresAt = new HashMap<>();

  public void on(PaymentAuthorized event) {
    expiresAt.put(event.paymentId(), event.expiresAt());
  }

  public void on(PaymentCaptured event) {
    expiresAt.remove(event.paymentId());
  }

  public void on(PaymentVoided event) {
    expiresAt.remove(event.paymentId());
  }

  public void on(PaymentAuthorizationExpired event) {
    expiresAt.remove(event.paymentId());
  }

  public List<PaymentId> expiredAt(OffsetDateTime now) {
    return expiresAt.entrySet().stream()
      .filter(entry -> !entry.getValue().isAfter(now))
      .map(Map.Entry::getKey)
      .toList();
  }
}
