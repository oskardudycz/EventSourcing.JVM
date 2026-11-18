package io.eventdriven.distributedprocesses.ecommerce.orders.unpaid;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Scheduled;

public class ScheduledUnpaidOrdersWorker {
    private final UnpaidOrdersWorker worker;
    private final Supplier<OffsetDateTime> now;

    public ScheduledUnpaidOrdersWorker(UnpaidOrdersWorker worker, Supplier<OffsetDateTime> now) {
        this.worker = worker;
        this.now = now;
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    public void handleAll() {
        worker.handle(now.get());
    }
}
