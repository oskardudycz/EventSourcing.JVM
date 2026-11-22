package io.eventdriven.ecommerce;

import io.eventdriven.ecommerce.orders.unpaid.ScheduledUnpaidOrdersWorker;
import io.eventdriven.ecommerce.payments.authorization.ScheduledAuthorizationExpiryWorker;
import io.eventdriven.ecommerce.payments.authorization.ScheduledPaymentTimeoutWorker;
import io.eventdriven.testing.api.ApiE2ESpecification.TestAction;
import io.eventdriven.testing.api.ApiE2ESpecification.TestStep;
import io.eventdriven.testing.time.MovableClock;
import java.time.Duration;
import java.time.OffsetDateTime;

public class Time {
    private final MovableClock clock;
    private final ScheduledPaymentTimeoutWorker paymentTimeoutWorker;
    private final ScheduledAuthorizationExpiryWorker authorizationExpiryWorker;
    private final ScheduledUnpaidOrdersWorker unpaidOrdersWorker;

    public Time(
            MovableClock clock,
            ScheduledPaymentTimeoutWorker paymentTimeoutWorker,
            ScheduledAuthorizationExpiryWorker authorizationExpiryWorker,
            ScheduledUnpaidOrdersWorker unpaidOrdersWorker) {
        this.clock = clock;
        this.paymentTimeoutWorker = paymentTimeoutWorker;
        this.authorizationExpiryWorker = authorizationExpiryWorker;
        this.unpaidOrdersWorker = unpaidOrdersWorker;
    }

    public OffsetDateTime now() {
        return clock.get();
    }

    public TestStep passes(Duration duration) {
        return (TestAction) () -> {
            clock.moveBy(duration);
            paymentTimeoutWorker.handleAll();
            authorizationExpiryWorker.handleAll();
            unpaidOrdersWorker.handleAll();
        };
    }
}
