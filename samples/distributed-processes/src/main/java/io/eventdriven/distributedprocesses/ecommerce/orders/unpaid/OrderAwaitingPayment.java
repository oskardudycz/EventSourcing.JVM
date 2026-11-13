package io.eventdriven.distributedprocesses.ecommerce.orders.unpaid;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

public class OrderAwaitingPayment implements Versioned {
  private OrderId orderId;
  private @Nullable OffsetDateTime unpaidSince;
  private long version;

  public OrderAwaitingPayment() {
  }

  public OrderAwaitingPayment(OrderId orderId, @Nullable OffsetDateTime unpaidSince) {
    this.orderId = orderId;
    this.unpaidSince = unpaidSince;
  }

  public OrderId getOrderId() {
    return orderId;
  }

  public @Nullable OffsetDateTime getUnpaidSince() {
    return unpaidSince;
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
