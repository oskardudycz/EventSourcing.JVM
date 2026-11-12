package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.core.aggregates.AbstractAggregate;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.distributedprocesses.ecommerce.orders.OrderEvent.*;

public class Order extends AbstractAggregate<OrderEvent, OrderId> {
  public enum Status {
    Opened,
    Confirmed,
    Completed,
    Cancelled
  }

  private enum PaymentProgress {
    Pending,
    Authorized,
    Captured,
    Failed
  }

  private enum ShipmentProgress {
    Pending,
    Reserved,
    Sent,
    Delivered,
    Failed
  }

  private ShoppingCartId cartId;

  private UUID clientId;

  private PricedProductItem[] productItems;

  private double totalPrice;

  private Status status;

  private PaymentProgress payment;

  private ShipmentProgress shipment;

  private PaymentId paymentId;

  private ShipmentId shipmentId;

  private Order() {
  }

  public static Order empty() {
    return new Order();
  }

  public void initialize(
    OrderId orderId,
    ShoppingCartId cartId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice,
    OffsetDateTime now
  ) {
    if (status != null)
      return;

    enqueue(new OrderInitialized(
      orderId,
      cartId,
      clientId,
      productItems,
      totalPrice,
      now
    ));
  }

  public void recordPaymentAuthorization(PaymentId paymentId, OffsetDateTime authorizedAt) {
    if (status != Status.Opened || payment != PaymentProgress.Pending)
      return;

    enqueue(new OrderPaymentAuthorized(id, paymentId, authorizedAt));

    progress(authorizedAt);
  }

  public void recordStockReservation(ShipmentId shipmentId, OffsetDateTime reservedAt) {
    if (status != Status.Opened || shipment != ShipmentProgress.Pending)
      return;

    enqueue(new OrderStockReserved(id, shipmentId, reservedAt));

    progress(reservedAt);
  }

  public void recordPackageSent(String carrier, String trackingNumber, OffsetDateTime sentAt) {
    if (status != Status.Confirmed
      || payment != PaymentProgress.Authorized
      || shipment != ShipmentProgress.Reserved)
      return;

    enqueue(new OrderPackageSent(id, paymentId, shipmentId, carrier, trackingNumber, sentAt));
  }

  public void recordPaymentCapture(OffsetDateTime capturedAt) {
    if (status != Status.Confirmed || payment != PaymentProgress.Authorized || !hasShipped())
      return;

    enqueue(new OrderPaymentCaptured(id, paymentId, totalPrice, capturedAt));

    progress(capturedAt);
  }

  public void recordDelivery(OffsetDateTime deliveredAt) {
    if (status != Status.Confirmed || shipment != ShipmentProgress.Sent)
      return;

    enqueue(new OrderShipmentDelivered(id, shipmentId, deliveredAt));

    progress(deliveredAt);
  }

  public void recordPaymentFailure(OffsetDateTime failedAt) {
    if (!isOpen() || payment == PaymentProgress.Captured || payment == PaymentProgress.Failed)
      return;

    enqueue(new OrderPaymentFailed(id, failedAt));

    progress(failedAt);
  }

  public void recordShipmentFailure(OffsetDateTime failedAt) {
    if (!isOpen() || shipment != ShipmentProgress.Pending && shipment != ShipmentProgress.Reserved)
      return;

    enqueue(new OrderShipmentFailed(id, failedAt));

    progress(failedAt);
  }

  public void cancel(OrderCancellationReason cancellationReason, OffsetDateTime now) {
    if (!isOpen() || hasShipped())
      return;

    enqueue(cancelled(cancellationReason, now));
  }

  private boolean isOpen() {
    return status == Status.Opened || status == Status.Confirmed;
  }

  private boolean hasShipped() {
    return shipment == ShipmentProgress.Sent || shipment == ShipmentProgress.Delivered;
  }

  private void progress(OffsetDateTime now) {
    if (payment == PaymentProgress.Failed || shipment == ShipmentProgress.Failed) {
      // The other participant still has to say where it stands, because that is what decides
      // whether a hold must be released.
      if (payment == PaymentProgress.Pending || shipment == ShipmentProgress.Pending)
        return;

      // Goods that already left cannot be held back, so the order stays open
      // and a person has to collect the payment.
      if (hasShipped())
        return;

      var reason = payment == PaymentProgress.Failed
        ? OrderCancellationReason.PaymentFailed
        : OrderCancellationReason.ProductWasOutOfStock;

      enqueue(cancelled(reason, now));
      return;
    }

    if (status == Status.Opened
      && payment == PaymentProgress.Authorized
      && shipment == ShipmentProgress.Reserved) {
      enqueue(new OrderConfirmed(id, paymentId, shipmentId, now));
      return;
    }

    if (status == Status.Confirmed
      && payment == PaymentProgress.Captured
      && shipment == ShipmentProgress.Delivered) {
      enqueue(new OrderCompleted(id, now));
    }
  }

  private OrderCancelled cancelled(OrderCancellationReason reason, OffsetDateTime now) {
    return new OrderCancelled(
      id,
      paymentId,
      paymentStatus(),
      shipmentId,
      shipmentStatus(),
      reason,
      now
    );
  }

  private OrderPaymentStatus paymentStatus() {
    return payment == PaymentProgress.Authorized
      ? OrderPaymentStatus.Authorized
      : OrderPaymentStatus.NotAuthorized;
  }

  private OrderShipmentStatus shipmentStatus() {
    return shipment == ShipmentProgress.Reserved
      ? OrderShipmentStatus.Reserved
      : OrderShipmentStatus.NotReserved;
  }

  @Override
  public void evolve(OrderEvent event) {
    switch (event) {
      case OrderInitialized orderInitialized -> {
        id = orderInitialized.orderId();
        cartId = orderInitialized.cartId();
        clientId = orderInitialized.clientId();
        productItems = orderInitialized.productItems();
        totalPrice = orderInitialized.totalPrice();
        status = Status.Opened;
        payment = PaymentProgress.Pending;
        shipment = ShipmentProgress.Pending;
      }
      case OrderPaymentAuthorized paymentAuthorized -> {
        paymentId = paymentAuthorized.paymentId();
        payment = PaymentProgress.Authorized;
      }
      case OrderStockReserved stockReserved -> {
        shipmentId = stockReserved.shipmentId();
        shipment = ShipmentProgress.Reserved;
      }
      case OrderConfirmed confirmed -> status = Status.Confirmed;
      case OrderPaymentCaptured paymentCaptured -> payment = PaymentProgress.Captured;
      case OrderPackageSent packageSent -> shipment = ShipmentProgress.Sent;
      case OrderShipmentDelivered shipmentDelivered -> shipment = ShipmentProgress.Delivered;
      case OrderPaymentFailed paymentFailed -> payment = PaymentProgress.Failed;
      case OrderShipmentFailed shipmentFailed -> shipment = ShipmentProgress.Failed;
      case OrderCompleted completed -> status = Status.Completed;
      case OrderCancelled cancelled -> status = Status.Cancelled;
    }
  }
}
