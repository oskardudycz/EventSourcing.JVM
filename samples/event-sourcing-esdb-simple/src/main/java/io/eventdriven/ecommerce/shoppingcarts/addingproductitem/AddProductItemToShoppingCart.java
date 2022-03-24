package io.eventdriven.ecommerce.shoppingcarts.addingproductitem;

import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ProductItemAddedToShoppingCart;
import io.eventdriven.ecommerce.shoppingcarts.productitems.ProductItem;

import java.util.UUID;

public record AddProductItemToShoppingCart(
  UUID shoppingCartId,
  ProductItem productItem,
  Long expectedVersion
) {
  public static ProductItemAddedToShoppingCart handle(
    ProductPriceCalculator productPriceCalculator,
    AddProductItemToShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    var pricedProductItem = productPriceCalculator.calculate(command.productItem);

    shoppingCart.productItems().add(pricedProductItem);

    return new ProductItemAddedToShoppingCart(
      command.shoppingCartId,
      pricedProductItem
    );
  }
}
