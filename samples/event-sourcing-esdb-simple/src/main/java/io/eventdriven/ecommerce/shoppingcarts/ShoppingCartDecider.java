package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;

import java.time.OffsetDateTime;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;

public class ShoppingCartDecider {
  private final ProductPriceCalculator productPriceCalculator;

  public ShoppingCartDecider(ProductPriceCalculator productPriceCalculator) {
    this.productPriceCalculator = productPriceCalculator;
  }

  public ShoppingCartEvent handle(ShoppingCartCommand command, ShoppingCart shoppingCart) {
    return switch (command) {
      case OpenShoppingCart openShoppingCart:
        yield handle(openShoppingCart);
      case AddProductItemToShoppingCart addProductItemToShoppingCart:
        yield handle(productPriceCalculator, addProductItemToShoppingCart, shoppingCart);
      case RemoveProductItemFromShoppingCart removeProductItemFromShoppingCart:
        yield handle(removeProductItemFromShoppingCart, shoppingCart);
      case ConfirmShoppingCart confirmShoppingCart:
        yield handle(confirmShoppingCart, shoppingCart);
      case CancelShoppingCart cancelShoppingCart:
        yield handle(cancelShoppingCart, shoppingCart);
    };
  }

  private ShoppingCartOpened handle(OpenShoppingCart command) {
    return new ShoppingCartOpened(
      command.shoppingCartId(),
      command.clientId()
    );
  }

  private ProductItemAddedToShoppingCart handle(
    ProductPriceCalculator productPriceCalculator,
    AddProductItemToShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    var pricedProductItem = productPriceCalculator.calculate(command.productItem());

    shoppingCart.productItems().add(pricedProductItem);

    return new ProductItemAddedToShoppingCart(
      command.shoppingCartId(),
      pricedProductItem
    );
  }

  private ProductItemRemovedFromShoppingCart handle(
    RemoveProductItemFromShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    shoppingCart.productItems().assertThatCanRemove(command.productItem());

    return new ProductItemRemovedFromShoppingCart(
      command.shoppingCartId(),
      command.productItem()
    );
  }

  private ShoppingCartConfirmed handle(ConfirmShoppingCart command, ShoppingCart shoppingCart) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    return new ShoppingCartConfirmed(
      shoppingCart.id(),
      OffsetDateTime.now()
    );
  }

  private ShoppingCartCanceled handle(CancelShoppingCart command, ShoppingCart shoppingCart) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    return new ShoppingCartCanceled(
      shoppingCart.id(),
      OffsetDateTime.now()
    );
  }
}
