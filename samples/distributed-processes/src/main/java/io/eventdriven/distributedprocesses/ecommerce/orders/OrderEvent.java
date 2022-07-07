package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface OrderEvent {
  record OrderInitialized(
    UUID orderId,
    UUID clientId,
    PricedProductItem[] productItems,
    Double totalPrice,
    OffsetDateTime initializedAt
  ) implements OrderEvent {
  }

  record OrderCompleted(
    UUID orderId,
    OffsetDateTime completedAt
  ) implements OrderEvent {
  }

  record OrderCancelled(
    UUID OrderId,
    UUID paymentId,
    OrderCancellationReason Reason,
    OffsetDateTime cancelledAt
  ) implements OrderEvent {
  }
}
