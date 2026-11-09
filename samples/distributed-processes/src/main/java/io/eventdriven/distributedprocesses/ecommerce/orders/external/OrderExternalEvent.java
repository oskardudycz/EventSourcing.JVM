package io.eventdriven.distributedprocesses.ecommerce.orders.external;

import io.eventdriven.distributedprocesses.ecommerce.orders.OrderId;

import io.eventdriven.distributedprocesses.ecommerce.shipments.ShipmentId;
import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface OrderExternalEvent {
  record OrderConfirmed(
    OrderId orderId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice,
    OffsetDateTime confirmedAt
  ) implements OrderExternalEvent {
  }

  record OrderShipped(
    OrderId orderId,
    UUID clientId,
    ShipmentId shipmentId,
    String carrier,
    String trackingNumber,
    OffsetDateTime sentAt
  ) implements OrderExternalEvent {
  }

  record OrderCompleted(
    OrderId orderId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice,
    OffsetDateTime completedAt
  ) implements OrderExternalEvent {
  }

  record OrderCancelled(
    OrderId orderId,
    UUID clientId,
    PricedProductItem[] productItems,
    double totalPrice,
    Reason reason,
    OffsetDateTime cancelledAt
  ) implements OrderExternalEvent {
    public enum Reason {
      ProductsOutOfStock,
      PaymentFailed,
      Requested
    }
  }
}
