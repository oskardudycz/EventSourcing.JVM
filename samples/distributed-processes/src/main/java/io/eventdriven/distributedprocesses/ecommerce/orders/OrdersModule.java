package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.ecommerce.orders.pending.PendingOrders;
import io.eventdriven.distributedprocesses.ecommerce.orders.unpaid.ScheduledUnpaidOrdersWorker;

public record OrdersModule(
  OrderFacade facade,
  PendingOrders pendingOrders,
  ScheduledUnpaidOrdersWorker unpaidOrdersWorker
) {
}
