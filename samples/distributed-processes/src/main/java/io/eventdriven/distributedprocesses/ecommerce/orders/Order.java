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
    ClosedUnpaid,
    Cancelled
  }

  private enum PaymentProgress {
    Pending,
    Authorized,
    Captured,
    Failed,
    Retrying
  }

  private enum ShipmentProgress {
    Pending,
    Reserved,
    Sent,
    Rejected,
    Failed
  }

  private double totalPrice;

  private Status status;

  private PaymentProgress payment;

  private ShipmentProgress shipment;

  private PaymentId paymentId;

  private ShipmentId shipmentId;

  private int paymentAttempts;

  private OrderCancellationReason shipmentFailureReason;

  private Order() {
  }

  public static Order empty() {
    return new Order();
  }

  public void place(
    OrderId orderId,
    ShoppingCartId cartId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice,
    OffsetDateTime now
  ) {
    if (status != null)
      return;

    enqueue(new OrderPlaced(
      orderId,
      cartId,
      clientId,
      productItems,
      totalPrice,
      now
    ));
  }

  public void recordPaymentAuthorization(PaymentId paymentId, OffsetDateTime authorizedAt) {
    if (status == Status.Opened && payment == PaymentProgress.Pending) {
      enqueue(new OrderPaymentAuthorized(id, paymentId, authorizedAt));

      progress(authorizedAt);
      return;
    }

    if (status == Status.Confirmed && payment == PaymentProgress.Retrying && paymentId.equals(this.paymentId)) {
      enqueue(new OrderPaymentAuthorized(id, paymentId, authorizedAt));
      enqueue(new OrderPaymentDue(id, paymentId, authorizedAt));
    }
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

    enqueue(new OrderPackageSent(id, carrier, trackingNumber, sentAt));
    enqueue(new OrderPaymentDue(id, paymentId, sentAt));
  }

  public void recordPaymentCapture(OffsetDateTime capturedAt) {
    if (status != Status.Confirmed || payment != PaymentProgress.Authorized || !hasShipped())
      return;

    enqueue(new OrderPaymentCaptured(id, paymentId, totalPrice, capturedAt));

    progress(capturedAt);
  }

  public void recordPaymentFailure(OffsetDateTime failedAt) {
    if (!isOpen() || payment == PaymentProgress.Captured || payment == PaymentProgress.Failed)
      return;

    enqueue(new OrderPaymentFailed(id, failedAt));

    progress(failedAt);
  }

  public void recordShipmentFailure(OrderCancellationReason reason, OffsetDateTime failedAt) {
    if (!isOpen() || shipment != ShipmentProgress.Pending && shipment != ShipmentProgress.Reserved)
      return;

    enqueue(new OrderShipmentFailed(id, reason, failedAt));

    progress(failedAt);
  }

  public void retryPayment(OffsetDateTime now) {
    if (status != Status.Confirmed || !hasShipped() || payment != PaymentProgress.Failed)
      return;

    var nextPaymentId = PaymentId.derivedFrom(id.value(), paymentAttempts + 1);

    enqueue(new OrderPaymentRetried(id, nextPaymentId, totalPrice, now));
  }

  public void closeUnpaid(OffsetDateTime now) {
    if (status != Status.Confirmed
      || !hasShipped()
      || payment != PaymentProgress.Failed && payment != PaymentProgress.Retrying)
      return;

    enqueue(new OrderClosedUnpaid(id, now));
  }

  public void cancel(OrderCancellationReason cancellationReason, OffsetDateTime now) {
    if (status != Status.Opened)
      return;

    enqueue(cancelled(cancellationReason, now));
  }

  private boolean isOpen() {
    return status == Status.Opened || status == Status.Confirmed;
  }

  private boolean hasShipped() {
    return shipment == ShipmentProgress.Sent;
  }

  private void progress(OffsetDateTime now) {
    if (payment == PaymentProgress.Failed
      || shipment == ShipmentProgress.Failed
      || shipment == ShipmentProgress.Rejected) {
      // The other participant still has to say where it stands, because that is what decides
      // whether a hold must be released.
      if (payment == PaymentProgress.Pending || shipment == ShipmentProgress.Pending)
        return;

      // Goods that already left cannot be held back, so the order stays open
      // and waits for the client to pay again.
      if (hasShipped())
        return;

      var reason = payment == PaymentProgress.Failed
        ? OrderCancellationReason.PaymentFailed
        : shipmentFailureReason;

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
      && shipment == ShipmentProgress.Sent) {
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
    return shipment == ShipmentProgress.Reserved || shipment == ShipmentProgress.Rejected
      ? OrderShipmentStatus.Reserved
      : OrderShipmentStatus.NotReserved;
  }

  @Override
  public void evolve(OrderEvent event) {
    switch (event) {
      case OrderPlaced orderPlaced -> {
        id = orderPlaced.orderId();
        totalPrice = orderPlaced.totalPrice();
        status = Status.Opened;
        payment = PaymentProgress.Pending;
        shipment = ShipmentProgress.Pending;
        paymentAttempts = 1;
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
      case OrderPaymentDue paymentDue -> {
      }
      case OrderPaymentFailed paymentFailed -> payment = PaymentProgress.Failed;
      case OrderShipmentFailed shipmentFailed -> {
        shipment = shipmentFailed.reason() == OrderCancellationReason.CouldNotShip
          ? ShipmentProgress.Rejected
          : ShipmentProgress.Failed;
        shipmentFailureReason = shipmentFailed.reason();
      }
      case OrderPaymentRetried paymentRetried -> {
        paymentId = paymentRetried.paymentId();
        paymentAttempts++;
        payment = PaymentProgress.Retrying;
      }
      case OrderClosedUnpaid closedUnpaid -> status = Status.ClosedUnpaid;
      case OrderCompleted completed -> status = Status.Completed;
      case OrderCancelled cancelled -> status = Status.Cancelled;
    }
  }
}
