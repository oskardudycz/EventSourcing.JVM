package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface ShoppingCartEvent {

  record ShoppingCartOpened(
    UUID clientId
  ) implements ShoppingCartEvent {
  }

  record ProductItemAddedToShoppingCart(
    PricedProductItem productItem
  ) implements ShoppingCartEvent {
  }

  record ProductItemRemovedFromShoppingCart(
    PricedProductItem productItem
  ) implements ShoppingCartEvent {
  }

  record ShoppingCartConfirmed(
    OffsetDateTime confirmedAt
  ) implements ShoppingCartEvent {
  }

  record ShoppingCartCanceled(
    OffsetDateTime canceledAt
  ) implements ShoppingCartEvent {
  }
}
