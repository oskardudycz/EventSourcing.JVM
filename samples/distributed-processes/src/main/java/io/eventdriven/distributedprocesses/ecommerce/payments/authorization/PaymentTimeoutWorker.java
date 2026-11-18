package io.eventdriven.distributedprocesses.ecommerce.payments.authorization;

import static io.eventdriven.distributedprocesses.ecommerce.payments.PaymentCommand.TimeOutPayment;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;
import java.time.OffsetDateTime;
import java.util.List;

public class PaymentTimeoutWorker {
    private final PendingPayments pendingPayments;
    private final MessageBus messageBus;

    public PaymentTimeoutWorker(PendingPayments pendingPayments, MessageBus messageBus) {
        this.pendingPayments = pendingPayments;
        this.messageBus = messageBus;
    }

    public void handle(OffsetDateTime now) {
        for (var paymentId : pendingPayments.timedOutAt(now)) {
            messageBus.publish(List.of(new TimeOutPayment(paymentId, now)));
        }
    }
}
