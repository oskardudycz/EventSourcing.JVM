package io.eventdriven.ecommerce.shoppingcarts;

import java.time.OffsetDateTime;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCart.*;

public class ShoppingCartDecider {

  public ShoppingCartDecider() {
  }

  public ShoppingCartEvent handle(ShoppingCartCommand command, ShoppingCart shoppingCart) {
    return switch (command) {
      case OpenShoppingCart openShoppingCart:
        yield handle(shoppingCart, openShoppingCart);
      case AddProductItemToShoppingCart addProductItemToShoppingCart:
        yield handle(addProductItemToShoppingCart, shoppingCart);
      case RemoveProductItemFromShoppingCart removeProductItemFromShoppingCart:
        yield handle(removeProductItemFromShoppingCart, shoppingCart);
      case ConfirmShoppingCart confirmShoppingCart:
        yield handle(confirmShoppingCart, shoppingCart);
      case CancelShoppingCart cancelShoppingCart:
        yield handle(cancelShoppingCart, shoppingCart);
    };
  }

  private ShoppingCartOpened handle(ShoppingCart shoppingCart, OpenShoppingCart command) {
    if (!(shoppingCart instanceof Empty))
      throw new IllegalStateException("Opening shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new ShoppingCartOpened(
      command.clientId()
    );
  }

  private ProductItemAddedToShoppingCart handle(
    AddProductItemToShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (!(shoppingCart instanceof Pending))
      throw new IllegalStateException("Adding product item to cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new ProductItemAddedToShoppingCart(
      command.productItem()
    );
  }

  private ProductItemRemovedFromShoppingCart handle(
    RemoveProductItemFromShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (!(shoppingCart instanceof Pending pendingShoppingCart))
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    if (!pendingShoppingCart.productItems().canRemove(command.productItem()))
      throw new IllegalStateException("Not enough product items.");

    return new ProductItemRemovedFromShoppingCart(
      command.productItem()
    );
  }

  private ShoppingCartConfirmed handle(ConfirmShoppingCart command, ShoppingCart shoppingCart) {
    if (!(shoppingCart instanceof Pending))
      throw new IllegalStateException("Confirming shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new ShoppingCartConfirmed(
      OffsetDateTime.now()
    );
  }

  private ShoppingCartCanceled handle(CancelShoppingCart command, ShoppingCart shoppingCart) {
    if (!(shoppingCart instanceof Pending))
      throw new IllegalStateException("Canceling shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new ShoppingCartCanceled(
      OffsetDateTime.now()
    );
  }
}
