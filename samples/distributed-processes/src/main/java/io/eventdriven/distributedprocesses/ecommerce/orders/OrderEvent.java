package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface OrderEvent {
  record OrderInitialized(
    OrderId orderId,
    ShoppingCartId cartId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice,
    OffsetDateTime initializedAt
  ) implements OrderEvent {
  }

  record OrderPaymentAuthorized(
    OrderId orderId,
    PaymentId paymentId,
    OffsetDateTime authorizedAt
  ) implements OrderEvent {
  }

  record OrderStockReserved(
    OrderId orderId,
    ShipmentId shipmentId,
    OffsetDateTime reservedAt
  ) implements OrderEvent {
  }

  record OrderConfirmed(
    OrderId orderId,
    PaymentId paymentId,
    OffsetDateTime confirmedAt
  ) implements OrderEvent {
  }

  record OrderPaymentCaptured(
    OrderId orderId,
    PaymentId paymentId,
    ShipmentId shipmentId,
    double amount,
    OffsetDateTime capturedAt
  ) implements OrderEvent {
  }

  record OrderPackageSent(
    OrderId orderId,
    ShipmentId shipmentId,
    OffsetDateTime sentAt
  ) implements OrderEvent {
  }

  record OrderShipmentDelivered(
    OrderId orderId,
    ShipmentId shipmentId,
    OffsetDateTime deliveredAt
  ) implements OrderEvent {
  }

  record OrderPaymentFailed(
    OrderId orderId,
    OffsetDateTime failedAt
  ) implements OrderEvent {
  }

  record OrderShipmentFailed(
    OrderId orderId,
    OffsetDateTime failedAt
  ) implements OrderEvent {
  }

  record OrderCompleted(
    OrderId orderId,
    OffsetDateTime completedAt
  ) implements OrderEvent {
  }

  record OrderCancelled(
    OrderId orderId,
    PaymentId paymentId, // nullable — an order cancelled before the authorisation has none
    OrderPaymentState paymentState,
    ShipmentId shipmentId, // nullable — an order cancelled before the reservation has none
    OrderShipmentState shipmentState,
    OrderCancellationReason reason,
    OffsetDateTime cancelledAt
  ) implements OrderEvent {
  }
}
