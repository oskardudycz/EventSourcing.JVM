package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface ShoppingCartEvent {

  record ShoppingCartOpened(
    UUID shoppingCartId,
    UUID clientId
  ) implements ShoppingCartEvent {
  }

  record ProductItemAddedToShoppingCart(
    UUID shoppingCartId,
    PricedProductItem productItem
  ) implements ShoppingCartEvent {
  }

  record ProductItemRemovedFromShoppingCart(
    UUID shoppingCartId,
    PricedProductItem productItem
  ) implements ShoppingCartEvent {
  }

  record ShoppingCartConfirmed(
    UUID shoppingCartId,
    OffsetDateTime confirmedAt
  ) implements ShoppingCartEvent {
  }

  record ShoppingCartCanceled(
    UUID shoppingCartId,
    OffsetDateTime canceledAt
  ) implements ShoppingCartEvent {
  }
}
