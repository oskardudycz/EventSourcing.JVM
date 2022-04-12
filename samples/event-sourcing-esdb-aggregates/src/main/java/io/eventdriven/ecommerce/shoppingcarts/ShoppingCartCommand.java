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
    ProductItem productItem,
    Long expectedVersion
  ) implements ShoppingCartCommand {
  }

  record RemoveProductItemFromShoppingCart(
    UUID shoppingCartId,
    PricedProductItem productItem,
    Long expectedVersion
  ) implements ShoppingCartCommand {
  }

  record ConfirmShoppingCart(
    UUID shoppingCartId,
    Long expectedVersion
  ) implements ShoppingCartCommand {
  }

  record CancelShoppingCart(
    UUID shoppingCartId,
    Long expectedVersion
  ) implements ShoppingCartCommand {
  }
}
