package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ScheduledAuthorizationExpiryWorker {
  private final AuthorizationExpiryWorker worker;
  private final Supplier<OffsetDateTime> now;

  public ScheduledAuthorizationExpiryWorker(AuthorizationExpiryWorker worker, Supplier<OffsetDateTime> now) {
    this.worker = worker;
    this.now = now;
  }

  @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
  public void handleAll() {
    worker.handle(now.get());
  }
}
