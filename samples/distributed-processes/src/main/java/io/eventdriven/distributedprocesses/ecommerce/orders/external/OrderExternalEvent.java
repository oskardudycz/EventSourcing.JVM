package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;

import io.eventdriven.distributedprocesses.ecommerce.payments.PaymentId;

import io.eventdriven.distributedprocesses.ecommerce.orders.OrderCancellationReason;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderPaymentState;
import io.eventdriven.distributedprocesses.ecommerce.orders.OrderShipmentState;
import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface OrderExternalEvent {
  record OrderInitialized(
    OrderId orderId,
    ShoppingCartId cartId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice,
    OffsetDateTime initializedAt
  ) implements OrderExternalEvent {
  }

  record OrderConfirmed(
    OrderId orderId,
    PaymentId paymentId,
    OffsetDateTime confirmedAt
  ) implements OrderExternalEvent {
  }

  record OrderPaymentCaptured(
    OrderId orderId,
    ShipmentId shipmentId,
    double amount,
    OffsetDateTime capturedAt
  ) implements OrderExternalEvent {
  }

  record OrderCompleted(
    OrderId orderId,
    OffsetDateTime completedAt
  ) implements OrderExternalEvent {
  }

  record OrderCancelled(
    OrderId orderId,
    PaymentId paymentId,
    OrderPaymentState paymentState,
    ShipmentId shipmentId,
    OrderShipmentState shipmentState,
    OrderCancellationReason reason,
    OffsetDateTime cancelledAt
  ) implements OrderExternalEvent {
  }
}
