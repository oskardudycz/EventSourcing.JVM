package io.eventdriven.distributedprocesses.ecommerce.orders.pending;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public class PendingOrder implements Versioned {
  private OrderId orderId;
  private UUID clientId;
  private double totalPrice;
  private OffsetDateTime initializedAt;
  private OrderPaymentStatus paymentStatus;
  private OrderShipmentStatus shipmentStatus;
  private long version;

  public PendingOrder() {
  }

  public PendingOrder(
    OrderId orderId,
    UUID clientId,
    double totalPrice,
    OffsetDateTime initializedAt,
    OrderPaymentStatus paymentStatus,
    OrderShipmentStatus shipmentStatus
  ) {
    this.orderId = orderId;
    this.clientId = clientId;
    this.totalPrice = totalPrice;
    this.initializedAt = initializedAt;
    this.paymentStatus = paymentStatus;
    this.shipmentStatus = shipmentStatus;
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

  public OrderPaymentStatus getPaymentStatus() {
    return paymentStatus;
  }

  public void setPaymentStatus(OrderPaymentStatus paymentStatus) {
    this.paymentStatus = paymentStatus;
  }

  public OrderShipmentStatus getShipmentStatus() {
    return shipmentStatus;
  }

  public void setShipmentStatus(OrderShipmentStatus shipmentStatus) {
    this.shipmentStatus = shipmentStatus;
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
