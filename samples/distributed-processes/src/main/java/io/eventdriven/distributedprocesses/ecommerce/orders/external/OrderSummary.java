package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.orders.Order;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentStatus;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import java.util.UUID;

public class OrderSummary implements Versioned {
  private OrderId orderId;
  private ShoppingCartId cartId;
  private UUID clientId;
  private PricedProductItem[] productItems;
  private double totalPrice;
  private Order.Status status;
  private PaymentId paymentId;
  private ShipmentId shipmentId;
  private OrderPaymentStatus paymentStatus;
  private OrderShipmentStatus shipmentStatus;
  private long version;

  public OrderSummary() {
  }

  public OrderSummary(
    OrderId orderId,
    ShoppingCartId cartId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice,
    Order.Status status,
    OrderPaymentStatus paymentStatus,
    OrderShipmentStatus shipmentStatus
  ) {
    this.orderId = orderId;
    this.cartId = cartId;
    this.clientId = clientId;
    this.productItems = productItems;
    this.totalPrice = totalPrice;
    this.status = status;
    this.paymentStatus = paymentStatus;
    this.shipmentStatus = shipmentStatus;
  }

  public OrderId getOrderId() {
    return orderId;
  }

  public void setOrderId(OrderId orderId) {
    this.orderId = orderId;
  }

  public ShoppingCartId getCartId() {
    return cartId;
  }

  public void setCartId(ShoppingCartId cartId) {
    this.cartId = cartId;
  }

  public UUID getClientId() {
    return clientId;
  }

  public void setClientId(UUID clientId) {
    this.clientId = clientId;
  }

  public PricedProductItem[] getProductItems() {
    return productItems;
  }

  public void setProductItems(PricedProductItem[] productItems) {
    this.productItems = productItems;
  }

  public double getTotalPrice() {
    return totalPrice;
  }

  public void setTotalPrice(double totalPrice) {
    this.totalPrice = totalPrice;
  }

  public Order.Status getStatus() {
    return status;
  }

  public void setStatus(Order.Status status) {
    this.status = status;
  }

  public PaymentId getPaymentId() {
    return paymentId;
  }

  public void setPaymentId(PaymentId paymentId) {
    this.paymentId = paymentId;
  }

  public ShipmentId getShipmentId() {
    return shipmentId;
  }

  public void setShipmentId(ShipmentId shipmentId) {
    this.shipmentId = shipmentId;
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
