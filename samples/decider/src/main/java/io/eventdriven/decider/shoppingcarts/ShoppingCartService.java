package io.eventdriven.decider.shoppingcarts;

import io.eventdriven.decider.core.processing.Decider;
import io.eventdriven.decider.shoppingcarts.productitems.ProductPriceCalculator;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

import static io.eventdriven.decider.shoppingcarts.ShoppingCart.EmptyShoppingCart;
import static io.eventdriven.decider.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.decider.shoppingcarts.ShoppingCartEvent.*;

public final class ShoppingCartService {
  public static Decider<ShoppingCart, ShoppingCartCommand, ShoppingCartEvent> shoppingCartDecider(
    Supplier<ProductPriceCalculator> getProductPriceCalculator
  ) {
    return new Decider<>(
      (command, state) -> ShoppingCartService.decide(getProductPriceCalculator, command, state),
      ShoppingCart::evolve,
      EmptyShoppingCart::new
    );
  }

  public static ShoppingCartEvent[] decide(
    Supplier<ProductPriceCalculator> getProductPriceCalculator,
    ShoppingCartCommand command,
    ShoppingCart state
  ) {
    return new ShoppingCartEvent[]{
      switch (command) {
        case OpenShoppingCart openCommand -> open(openCommand);
        case AddProductItemToShoppingCart addCommand ->
          addProductItem(getProductPriceCalculator.get(), addCommand, state);
        case RemoveProductItemFromShoppingCart removeProductCommand ->
          removeProductItem(removeProductCommand, state);
        case ConfirmShoppingCart confirmCommand ->
          confirm(confirmCommand, state);
        case CancelShoppingCart cancelCommand -> cancel(cancelCommand, state);
      }
    };
  }

  public static ShoppingCartOpened open(OpenShoppingCart command) {
    return new ShoppingCartOpened(
      command.shoppingCartId(),
      command.clientId()
    );
  }

  public static ProductItemAddedToShoppingCart addProductItem(
    ProductPriceCalculator productPriceCalculator,
    AddProductItemToShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (!(shoppingCart instanceof ShoppingCart.PendingShoppingCart pendingShoppingCart))
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    var pricedProductItem = productPriceCalculator.calculate(command.productItem());

    pendingShoppingCart.productItems().add(pricedProductItem);

    return new ProductItemAddedToShoppingCart(
      command.shoppingCartId(),
      pricedProductItem
    );
  }

  public static ProductItemRemovedFromShoppingCart removeProductItem(
    RemoveProductItemFromShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (!(shoppingCart instanceof ShoppingCart.PendingShoppingCart pendingShoppingCart))
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    pendingShoppingCart.productItems().hasEnough(command.productItem());

    return new ProductItemRemovedFromShoppingCart(
      command.shoppingCartId(),
      command.productItem()
    );
  }

  public static ShoppingCartConfirmed confirm(ConfirmShoppingCart command, ShoppingCart shoppingCart) {
    if (!(shoppingCart instanceof ShoppingCart.PendingShoppingCart pendingShoppingCart))
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new ShoppingCartConfirmed(
      pendingShoppingCart.id(),
      OffsetDateTime.now()
    );
  }

  public static ShoppingCartCanceled cancel(CancelShoppingCart command, ShoppingCart shoppingCart) {
    if (!(shoppingCart instanceof ShoppingCart.PendingShoppingCart pendingShoppingCart))
      throw new IllegalStateException("Removing product item for cart in '%s' status is not allowed.".formatted(shoppingCart.getClass().getName()));

    return new ShoppingCartCanceled(
      pendingShoppingCart.id(),
      OffsetDateTime.now()
    );
  }
}
