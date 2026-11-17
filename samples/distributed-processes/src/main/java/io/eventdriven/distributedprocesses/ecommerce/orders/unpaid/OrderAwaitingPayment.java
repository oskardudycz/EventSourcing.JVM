package io.eventdriven.distributedprocesses.ecommerce.orders.unpaid;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import java.time.OffsetDateTime;

public class OrderAwaitingPayment implements Versioned {
  private OrderId orderId;
  private OffsetDateTime dueAt;
  private long version;

  public OrderAwaitingPayment() {
  }

  public OrderAwaitingPayment(OrderId orderId, OffsetDateTime dueAt) {
    this.orderId = orderId;
    this.dueAt = dueAt;
  }

  public OrderId getOrderId() {
    return orderId;
  }

  public OffsetDateTime getDueAt() {
    return dueAt;
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
