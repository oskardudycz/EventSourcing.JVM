package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.pricing.ProductPriceCalculator;

import java.time.OffsetDateTime;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCart.*;

public class ShoppingCartDecider {
  private final ProductPriceCalculator productPriceCalculator;

  public ShoppingCartDecider(ProductPriceCalculator productPriceCalculator) {
    this.productPriceCalculator = productPriceCalculator;
  }

  public ShoppingCartEvent handle(ShoppingCartCommand command, ShoppingCart shoppingCart) {
    return switch (command) {
      case OpenShoppingCart openShoppingCart:
        yield handle(shoppingCart, openShoppingCart);
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

  private ShoppingCartOpened handle(ShoppingCart shoppingCart, OpenShoppingCart command) {
    if (!(shoppingCart instanceof Initial))
      throw new IllegalStateException("Opening shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

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
    if (!(shoppingCart instanceof Pending pendingShoppingCart))
      throw new IllegalStateException("Adding product item to cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    var pricedProductItem = productPriceCalculator.calculate(command.productItem());

    pendingShoppingCart.productItems().add(pricedProductItem);

    return new ProductItemAddedToShoppingCart(
      command.shoppingCartId(),
      pricedProductItem
    );
  }

  private ProductItemRemovedFromShoppingCart handle(
    RemoveProductItemFromShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (!(shoppingCart instanceof Pending pendingShoppingCart))
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    pendingShoppingCart.productItems().assertThatCanRemove(command.productItem());

    return new ProductItemRemovedFromShoppingCart(
      command.shoppingCartId(),
      command.productItem()
    );
  }

  private ShoppingCartConfirmed handle(ConfirmShoppingCart command, ShoppingCart shoppingCart) {
    if (!(shoppingCart instanceof Pending pendingShoppingCart))
      throw new IllegalStateException("Confirming shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new ShoppingCartConfirmed(
      pendingShoppingCart.id(),
      OffsetDateTime.now()
    );
  }

  private ShoppingCartCanceled handle(CancelShoppingCart command, ShoppingCart shoppingCart) {
    if (!(shoppingCart instanceof Pending pendingShoppingCart))
      throw new IllegalStateException("Canceling shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new ShoppingCartCanceled(
      pendingShoppingCart.id(),
      OffsetDateTime.now()
    );
  }
}
