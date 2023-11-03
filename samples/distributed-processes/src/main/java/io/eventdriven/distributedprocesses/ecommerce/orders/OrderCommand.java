package io.eventdriven.distributedprocesses.ecommerce.orders;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface OrderCommand {
  record InitializeOrder(
      UUID OrderId, UUID ClientId, PricedProductItem[] ProductItems, double TotalPrice)
      implements OrderCommand {}

  record RecordOrderPayment(UUID OrderId, UUID PaymentId, OffsetDateTime PaymentRecordedAt)
      implements OrderCommand {}

  record CompleteOrder(UUID OrderId) implements OrderCommand {}

  record CancelOrder(UUID OrderId, OrderCancellationReason CancellationReason)
      implements OrderCommand {}
}
