package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;

import java.util.UUID;

public sealed interface ShoppingCartCommand {
  record OpenShoppingCart(
    UUID shoppingCartId,
    UUID clientId
  ) implements ShoppingCartCommand {
  }

  record AddProductItemToShoppingCart(
    UUID shoppingCartId,
    ProductItem productItem
  ) implements ShoppingCartCommand {
  }

  record ConfirmShoppingCart(
    UUID shoppingCartId
  ) implements ShoppingCartCommand {
  }

  record RemoveProductItemFromShoppingCart(
    UUID shoppingCartId,
    PricedProductItem productItem
  ) implements ShoppingCartCommand {
  }

  record CancelShoppingCart(
    UUID shoppingCartId
  ) implements ShoppingCartCommand {
  }
}
