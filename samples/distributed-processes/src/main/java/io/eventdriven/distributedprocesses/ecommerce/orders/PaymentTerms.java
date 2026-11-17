package io.eventdriven.distributedprocesses.ecommerce.orders;

import java.time.Duration;
import java.time.OffsetDateTime;

public interface PaymentTerms {
  OffsetDateTime dueAt(OffsetDateTime failedAt);

  static PaymentTerms within(Duration timeToPay) {
    return failedAt -> failedAt.plus(timeToPay);
  }
}
