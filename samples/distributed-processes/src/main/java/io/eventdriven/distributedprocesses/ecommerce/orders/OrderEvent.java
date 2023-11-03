package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.ecommerce.orders.products.PricedProductItem;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.lang.Nullable;

public sealed interface OrderEvent {
  record OrderInitialized(
      UUID orderId,
      UUID clientId,
      PricedProductItem[] productItems,
      double totalPrice,
      OffsetDateTime initializedAt)
      implements OrderEvent {}

  record OrderPaymentRecorded(
      UUID orderId,
      UUID paymentId,
      PricedProductItem[] productItems,
      double amount,
      OffsetDateTime paymentRecordedAt)
      implements OrderEvent {}

  record OrderCompleted(UUID orderId, OffsetDateTime completedAt) implements OrderEvent {}

  record OrderCancelled(
      UUID OrderId,
      @Nullable UUID paymentId,
      OrderCancellationReason Reason,
      OffsetDateTime cancelledAt)
      implements OrderEvent {}
}
