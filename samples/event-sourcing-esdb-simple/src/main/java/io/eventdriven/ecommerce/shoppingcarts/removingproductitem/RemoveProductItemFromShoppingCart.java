package io.eventdriven.ecommerce.shoppingcarts.removingproductitem;

import io.eventdriven.ecommerce.pricing.IProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.util.UUID;

public record RemoveProductItemFromShoppingCart(
  UUID shoppingCartId,
  PricedProductItem productItem
)
{
  public static RemoveProductItemFromShoppingCart From(UUID cartId, PricedProductItem productItem)
  {
    if (cartId == null)
      throw new IllegalArgumentException("Cart id has to be defined");

    if (productItem == null)
      throw new IllegalArgumentException("Product item has to be defined");

    return new RemoveProductItemFromShoppingCart(cartId, productItem);
  }

  public static Events.ProductItemAddedToShoppingCart Handle(
    IProductPriceCalculator productPriceCalculator,
    RemoveProductItemFromShoppingCart command,
    ShoppingCart shoppingCart
  )
  {
    if(shoppingCart.isClosed())
      throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    shoppingCart.productItems().remove(command.productItem());

    return new Events.ProductItemAddedToShoppingCart(
      command.shoppingCartId,
      command.productItem()
    );
  }
}
