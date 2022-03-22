package io.eventdriven.ecommerce.shoppingcarts.addingproductitem;

import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;

import java.util.UUID;

public record AddProductItemToShoppingCart(
  UUID shoppingCartId,
  ProductItem productItem,
  Long expectedVersion
) {
  public static AddProductItemToShoppingCart From(UUID cartId, ProductItem productItem, Long expectedVersion) {
    if (cartId == null)
      throw new IllegalArgumentException("Cart id has to be defined");

    if (productItem == null)
      throw new IllegalArgumentException("Product item has to be defined");

    if (expectedVersion == null)
      throw new IllegalArgumentException("Expected version has to be provided");

    return new AddProductItemToShoppingCart(cartId, productItem, expectedVersion);
  }

  public static Events.ProductItemAddedToShoppingCart Handle(
    ProductPriceCalculator productPriceCalculator,
    AddProductItemToShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    var pricedProductItem = productPriceCalculator.Calculate(command.productItem);

    shoppingCart.productItems().add(pricedProductItem);

    return new Events.ProductItemAddedToShoppingCart(
      command.shoppingCartId,
      pricedProductItem
    );
  }
}
