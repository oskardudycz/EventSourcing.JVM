package io.eventdriven.ecommerce.shoppingcarts.removingproductitem;

import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.util.UUID;

public record RemoveProductItemFromShoppingCart(
  UUID shoppingCartId,
  PricedProductItem productItem,
  Long expectedVersion
) {
  public static RemoveProductItemFromShoppingCart of(UUID cartId, PricedProductItem productItem, Long expectedVersion) {
    if (cartId == null)
      throw new IllegalArgumentException("Cart id has to be defined");

    if (productItem == null)
      throw new IllegalArgumentException("Product item has to be defined");

    if (expectedVersion == null)
      throw new IllegalArgumentException("Expected version has to be provided");

    return new RemoveProductItemFromShoppingCart(cartId, productItem, expectedVersion);
  }

  public static Events.ProductItemRemovedFromShoppingCart handle(
    RemoveProductItemFromShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    shoppingCart.productItems().remove(command.productItem());

    return new Events.ProductItemRemovedFromShoppingCart(
      command.shoppingCartId,
      command.productItem()
    );
  }
}
