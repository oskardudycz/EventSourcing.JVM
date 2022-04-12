package io.eventdriven.ecommerce.shoppingcarts.removingproductitem;

import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemRemovedFromShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.util.UUID;

public record RemoveProductItemFromShoppingCart(
  UUID shoppingCartId,
  PricedProductItem productItem,
  Long expectedVersion
) {
  public static ProductItemRemovedFromShoppingCart handle(
    RemoveProductItemFromShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    shoppingCart.productItems().assertThatCanRemove(command.productItem());

    return new ProductItemRemovedFromShoppingCart(
      command.shoppingCartId,
      command.productItem()
    );
  }
}
