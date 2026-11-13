package io.eventdriven.distributedprocesses.ecommerce.orders.unpaid;

import io.eventdriven.distributedprocesses.core.messaging.MessageBus;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderCommand.CloseUnpaidOrder;

public class UnpaidOrdersWorker {
  private final OrdersAwaitingPayment ordersAwaitingPayment;
  private final MessageBus messageBus;
  private final Duration paymentRetryPeriod;

  public UnpaidOrdersWorker(
    OrdersAwaitingPayment ordersAwaitingPayment,
    MessageBus messageBus,
    Duration paymentRetryPeriod
  ) {
    this.ordersAwaitingPayment = ordersAwaitingPayment;
    this.messageBus = messageBus;
    this.paymentRetryPeriod = paymentRetryPeriod;
  }

  public void handle(OffsetDateTime now) {
    for (var orderId : ordersAwaitingPayment.unpaidSinceBefore(now.minus(paymentRetryPeriod))) {
      messageBus.publish(List.of(new CloseUnpaidOrder(orderId)));
    }
  }
}
