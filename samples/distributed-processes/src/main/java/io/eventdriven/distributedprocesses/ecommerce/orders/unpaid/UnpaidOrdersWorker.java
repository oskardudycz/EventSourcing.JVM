package io.eventdriven.distributedprocesses.ecommerce.orders.unpaid;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;

import java.time.OffsetDateTime;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.CloseUnpaidOrder;

public class UnpaidOrdersWorker {
  private final OrdersAwaitingPayment ordersAwaitingPayment;
  private final MessageBus messageBus;

  public UnpaidOrdersWorker(
    OrdersAwaitingPayment ordersAwaitingPayment,
    MessageBus messageBus
  ) {
    this.ordersAwaitingPayment = ordersAwaitingPayment;
    this.messageBus = messageBus;
  }

  public void handle(OffsetDateTime now) {
    for (var orderId : ordersAwaitingPayment.overdueAt(now)) {
      messageBus.publish(List.of(new CloseUnpaidOrder(orderId)));
    }
  }
}
