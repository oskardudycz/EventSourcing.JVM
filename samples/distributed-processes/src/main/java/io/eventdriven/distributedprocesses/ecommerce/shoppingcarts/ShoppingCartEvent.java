package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.time.OffsetDateTime;
import java.util.UUID;

public sealed interface ShoppingCartEvent {

  record ProductItemAddedToShoppingCart(
    ShoppingCartId shoppingCartId,
    UUID clientId,
    PricedProductItem productItem,
    OffsetDateTime addedAt
  ) implements ShoppingCartEvent {
  }

  record ProductItemRemovedFromShoppingCart(
    ShoppingCartId shoppingCartId,
    PricedProductItem productItem,
    OffsetDateTime removedAt
  ) implements ShoppingCartEvent {
  }

  record ProductItemPriceChanged(
    ShoppingCartId shoppingCartId,
    UUID productId,
    double oldUnitPrice,
    double newUnitPrice,
    OffsetDateTime changedAt
  ) implements ShoppingCartEvent {
  }

  record ShoppingCartCheckedOut(
    ShoppingCartId shoppingCartId,
    OffsetDateTime checkedOutAt
  ) implements ShoppingCartEvent {
  }
}


