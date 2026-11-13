package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;

import java.util.UUID;

public sealed interface ShoppingCartCommand {
  record AddProductItemToShoppingCart(
    UUID clientId,
    ProductItem productItem
  ) implements ShoppingCartCommand {
  }

  record RemoveProductItemFromShoppingCart(
    ShoppingCartId shoppingCartId,
    ProductItem productItem,
    Long expectedVersion
  ) implements ShoppingCartCommand {
  }

  record CheckOutShoppingCart(
    ShoppingCartId shoppingCartId,
    Long expectedVersion
  ) implements ShoppingCartCommand {
  }
}
