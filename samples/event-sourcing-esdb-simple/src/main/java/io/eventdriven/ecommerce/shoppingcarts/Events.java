package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.time.LocalDateTime;
import java.util.UUID;

public final class Events {

  public sealed interface ShoppingCartEvent permits
    ShoppingCartOpened,
    ProductItemAddedToShoppingCart,
    ProductItemRemovedFromShoppingCart,
    ShoppingCartConfirmed,
    ShoppingCartCanceled {
  }

  public record ShoppingCartOpened(
    UUID shoppingCartId,
    UUID clientId
  ) implements ShoppingCartEvent {
  }

  public record ProductItemAddedToShoppingCart(
    UUID shoppingCartId,
    PricedProductItem productItem
  ) implements ShoppingCartEvent {
  }

  public record ProductItemRemovedFromShoppingCart(
    UUID shoppingCartId,
    PricedProductItem productItem
  ) implements ShoppingCartEvent {
  }

  public record ShoppingCartConfirmed(
    UUID shoppingCartId,
    LocalDateTime confirmedAt
  ) implements ShoppingCartEvent {
  }

  public record ShoppingCartCanceled(
    UUID shoppingCartId,
    LocalDateTime canceledAt
  ) implements ShoppingCartEvent {
  }
}


