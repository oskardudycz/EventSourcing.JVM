package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.core.projections.Versioned;
import io.eventdriven.distributedprocesses.ecommerce.orders.Order;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentState;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentState;
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
  private OrderPaymentState paymentState;
  private OrderShipmentState shipmentState;
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
    OrderPaymentState paymentState,
    OrderShipmentState shipmentState
  ) {
    this.orderId = orderId;
    this.cartId = cartId;
    this.clientId = clientId;
    this.productItems = productItems;
    this.totalPrice = totalPrice;
    this.status = status;
    this.paymentState = paymentState;
    this.shipmentState = shipmentState;
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
