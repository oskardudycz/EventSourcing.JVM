package io.eventdriven.distributedprocesses.ecommerce.payments;

import java.time.Duration;
import java.time.OffsetDateTime;

public interface PaymentValidity {
  OffsetDateTime timesOutAt(OffsetDateTime requestedAt);

  static PaymentValidity of(Duration validityTime) {
    return requestedAt -> requestedAt.plus(validityTime);
  }
}
