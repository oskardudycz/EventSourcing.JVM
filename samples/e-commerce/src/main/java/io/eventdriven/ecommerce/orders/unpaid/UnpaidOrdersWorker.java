package io.eventdriven.ecommerce.orders.unpaid;

import static io.eventdriven.ecommerce.orders.OrderCommand.CloseUnpaidOrder;

import io.eventdriven.core.messaging.MessageBus;
import java.time.OffsetDateTime;
import java.util.List;

public class UnpaidOrdersWorker {
    private final OrdersAwaitingPayment ordersAwaitingPayment;
    private final MessageBus messageBus;

    public UnpaidOrdersWorker(OrdersAwaitingPayment ordersAwaitingPayment, MessageBus messageBus) {
        this.ordersAwaitingPayment = ordersAwaitingPayment;
        this.messageBus = messageBus;
    }

    public void handle(OffsetDateTime now) {
        for (var orderId : ordersAwaitingPayment.overdueAt(now)) {
            messageBus.publish(List.of(new CloseUnpaidOrder(orderId)));
        }
    }
}
