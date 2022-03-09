package io.eventdriven.ecommerce.shoppingcarts.addingproductitem;

import io.eventdriven.ecommerce.pricing.IProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;

import java.util.UUID;

public record AddProductItemToShoppingCart(
  UUID shoppingCartId,
  ProductItem productItem
)
{
  public static AddProductItemToShoppingCart From(UUID cartId, ProductItem productItem)
  {
    if (cartId == null)
      throw new IllegalArgumentException("Cart id has to be defined");

    if (productItem == null)
      throw new IllegalArgumentException("Product item has to be defined");

    return new AddProductItemToShoppingCart(cartId, productItem);
  }

  public static Events.ProductItemRemovedFromShoppingCart Handle(
    IProductPriceCalculator productPriceCalculator,
    AddProductItemToShoppingCart command,
    ShoppingCart shoppingCart
  )
  {
    if(shoppingCart.isClosed())
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    var pricedProductItem = productPriceCalculator.Calculate(command.productItem);

    shoppingCart.productItems().add(pricedProductItem);

    return new Events.ProductItemRemovedFromShoppingCart(
      command.shoppingCartId,
      pricedProductItem
    );
  }
}
