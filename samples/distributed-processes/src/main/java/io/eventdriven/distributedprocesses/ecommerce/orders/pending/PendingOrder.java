package io.eventdriven.distributedprocesses.ecommerce.orders.pending;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;

public class PendingOrder implements Versioned {
  private OrderId orderId;
  private long version;

  public PendingOrder() {
  }

  public PendingOrder(OrderId orderId) {
    this.orderId = orderId;
  }

  public OrderId getOrderId() {
    return orderId;
  }

  @Override
  public long getVersion() {
    return version;
  }

  @Override
  public void setVersion(long version) {
    this.version = version;
  }
}
