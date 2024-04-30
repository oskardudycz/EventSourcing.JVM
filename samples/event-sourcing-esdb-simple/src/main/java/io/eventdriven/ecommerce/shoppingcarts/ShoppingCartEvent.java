package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface ShoppingCartEvent {

  record Opened(
    UUID clientId
  ) implements ShoppingCartEvent {
  }

  record ProductItemAdded(
    PricedProductItem productItem
  ) implements ShoppingCartEvent {
  }

  record ProductItemRemoved(
    PricedProductItem productItem
  ) implements ShoppingCartEvent {
  }

  record Confirmed(
    OffsetDateTime confirmedAt
  ) implements ShoppingCartEvent {
  }

  record Canceled(
    OffsetDateTime canceledAt
  ) implements ShoppingCartEvent {
  }
}
