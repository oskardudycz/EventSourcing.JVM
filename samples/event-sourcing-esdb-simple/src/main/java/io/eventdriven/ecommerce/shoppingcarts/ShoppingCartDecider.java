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

  private Opened handle(ShoppingCart shoppingCart, OpenShoppingCart command) {
    if (!(shoppingCart instanceof Empty))
      throw new IllegalStateException("Opening shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new Opened(
      command.clientId()
    );
  }

  private ProductItemAdded handle(
    AddProductItemToShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (!(shoppingCart instanceof Pending))
      throw new IllegalStateException("Adding product item to cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new ProductItemAdded(
      command.productItem()
    );
  }

  private ProductItemRemoved handle(
    RemoveProductItemFromShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (!(shoppingCart instanceof Pending pendingShoppingCart))
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    if (!pendingShoppingCart.productItems().canRemove(command.productItem()))
      throw new IllegalStateException("Not enough product items.");

    return new ProductItemRemoved(
      command.productItem()
    );
  }

  private Confirmed handle(ConfirmShoppingCart command, ShoppingCart shoppingCart) {
    if (!(shoppingCart instanceof Pending))
      throw new IllegalStateException("Confirming shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new Confirmed(
      OffsetDateTime.now()
    );
  }

  private Canceled handle(CancelShoppingCart command, ShoppingCart shoppingCart) {
    if (!(shoppingCart instanceof Pending))
      throw new IllegalStateException("Canceling shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new Canceled(
      OffsetDateTime.now()
    );
  }
}
