package io.eventdriven.ecommerce.shoppingcarts;

import java.time.OffsetDateTime;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCart.*;

public final class ShoppingCartDecider {

  public static ShoppingCartEvent handle(ShoppingCartCommand command, ShoppingCart shoppingCart) {
    return switch (command) {
      case Open openShoppingCart:
        yield handle(shoppingCart, openShoppingCart);
      case AddProductItem addProductItemToShoppingCart:
        yield handle(addProductItemToShoppingCart, shoppingCart);
      case RemoveProductItem removeProductItemFromShoppingCart:
        yield handle(removeProductItemFromShoppingCart, shoppingCart);
      case Confirm confirmShoppingCart:
        yield handle(confirmShoppingCart, shoppingCart);
      case Cancel cancelShoppingCart:
        yield handle(cancelShoppingCart, shoppingCart);
    };
  }

  private static Opened handle(ShoppingCart shoppingCart, Open command) {
    if (!(shoppingCart instanceof Empty))
      throw new IllegalStateException("Opening shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new Opened(
      command.clientId()
    );
  }

  private static ProductItemAdded handle(
    AddProductItem command,
    ShoppingCart shoppingCart
  ) {
    if (!(shoppingCart instanceof Pending))
      throw new IllegalStateException("Adding product item to cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new ProductItemAdded(
      command.productItem()
    );
  }

  private static ProductItemRemoved handle(
    RemoveProductItem command,
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

  private static Confirmed handle(Confirm ignore, ShoppingCart shoppingCart) {
    if (!(shoppingCart instanceof Pending))
      throw new IllegalStateException("Confirming shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new Confirmed(
      OffsetDateTime.now()
    );
  }

  private static Canceled handle(Cancel ignore, ShoppingCart shoppingCart) {
    if (!(shoppingCart instanceof Pending))
      throw new IllegalStateException("Canceling shopping cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new Canceled(
      OffsetDateTime.now()
    );
  }
}
