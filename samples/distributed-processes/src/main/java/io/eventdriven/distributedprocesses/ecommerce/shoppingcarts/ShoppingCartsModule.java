package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api.Carts;

public record ShoppingCartsModule(
  ShoppingCartFacade facade,
  CustomerShoppingCarts customerShoppingCarts,
  Carts carts
) {
}
