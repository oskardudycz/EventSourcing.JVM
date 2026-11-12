package io.eventdriven.distributedprocesses.ecommerce.orders.pending;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentState;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentState;

import java.time.OffsetDateTime;
import java.util.UUID;

public class PendingOrder implements Versioned {
  private OrderId orderId;
  private UUID clientId;
  private double totalPrice;
  private OffsetDateTime initializedAt;
  private OrderPaymentState paymentState;
  private OrderShipmentState shipmentState;
  private long version;

  public PendingOrder() {
  }

  public PendingOrder(
    OrderId orderId,
    UUID clientId,
    double totalPrice,
    OffsetDateTime initializedAt,
    OrderPaymentState paymentState,
    OrderShipmentState shipmentState
  ) {
    this.orderId = orderId;
    this.clientId = clientId;
    this.totalPrice = totalPrice;
    this.initializedAt = initializedAt;
    this.paymentState = paymentState;
    this.shipmentState = shipmentState;
  }

  public OrderId getOrderId() {
    return orderId;
  }

  public void setOrderId(OrderId orderId) {
    this.orderId = orderId;
  }

  public UUID getClientId() {
    return clientId;
  }

  public void setClientId(UUID clientId) {
    this.clientId = clientId;
  }

  public double getTotalPrice() {
    return totalPrice;
  }

  public void setTotalPrice(double totalPrice) {
    this.totalPrice = totalPrice;
  }

  public OffsetDateTime getInitializedAt() {
    return initializedAt;
  }

  public void setInitializedAt(OffsetDateTime initializedAt) {
    this.initializedAt = initializedAt;
  }

  public OrderPaymentState getPaymentState() {
    return paymentState;
  }

  public void setPaymentState(OrderPaymentState paymentState) {
    this.paymentState = paymentState;
  }

  public OrderShipmentState getShipmentState() {
    return shipmentState;
  }

  public void setShipmentState(OrderShipmentState shipmentState) {
    this.shipmentState = shipmentState;
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
