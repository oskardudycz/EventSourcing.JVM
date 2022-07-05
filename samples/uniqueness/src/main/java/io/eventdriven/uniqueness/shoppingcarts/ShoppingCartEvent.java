package io.eventdriven.uniqueness.shoppingcarts;

import io.eventdriven.uniqueness.shoppingcarts.productitems.PricedProductItem;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ShoppingCartEvent {

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
    LocalDateTime confirmedAt
  ) implements ShoppingCartEvent {
  }

  record ShoppingCartCanceled(
    UUID shoppingCartId,
    LocalDateTime canceledAt
  ) implements ShoppingCartEvent {
  }
}
