package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItem;

import java.util.UUID;

public sealed interface ShoppingCartCommand {
  record OpenShoppingCart(
    ShoppingCartId shoppingCartId,
    UUID clientId
  ) implements ShoppingCartCommand {
  }

  record AddProductItemToShoppingCart(
    ShoppingCartId shoppingCartId,
    ProductItem productItem,
    Long expectedVersion
  ) implements ShoppingCartCommand {
  }

  record RemoveProductItemFromShoppingCart(
    ShoppingCartId shoppingCartId,
    PricedProductItem productItem,
    Long expectedVersion
  ) implements ShoppingCartCommand {
  }

  record ConfirmShoppingCart(
    ShoppingCartId shoppingCartId,
    Long expectedVersion
  ) implements ShoppingCartCommand {
  }

  record CancelShoppingCart(
    ShoppingCartId shoppingCartId,
    Long expectedVersion
  ) implements ShoppingCartCommand {
  }
}
