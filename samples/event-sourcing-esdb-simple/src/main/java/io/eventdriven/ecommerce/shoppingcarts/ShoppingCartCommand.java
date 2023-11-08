package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;

import java.util.UUID;

public sealed interface ShoppingCartCommand {
  record OpenShoppingCart(
    UUID clientId
  ) implements ShoppingCartCommand {
  }

  record AddProductItemToShoppingCart(
    PricedProductItem productItem
  ) implements ShoppingCartCommand {
  }

  record RemoveProductItemFromShoppingCart(
    PricedProductItem productItem
  ) implements ShoppingCartCommand {
  }

  record ConfirmShoppingCart() implements ShoppingCartCommand {
  }

  record CancelShoppingCart() implements ShoppingCartCommand {
  }
}
