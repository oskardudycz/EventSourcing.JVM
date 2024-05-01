package io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable;

import io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ProductItems.ProductPriceCalculator;

import java.time.OffsetDateTime;
import java.util.UUID;

import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ProductItems.ProductItems.PricedProductItem;
import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ProductItems.ProductItems.ProductItem;
import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ShoppingCartEvent.*;
import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ShoppingCartService.ShoppingCartCommand.*;

public class ShoppingCartService {
  public sealed interface ShoppingCartCommand {
    record OpenShoppingCart(
      UUID shoppingCartId,
      UUID clientId
    ) implements ShoppingCartCommand {
    }

    record AddProductItemToShoppingCart(
      UUID shoppingCartId,
      ProductItem productItem
    ) implements ShoppingCartCommand {
    }

    record RemoveProductItemFromShoppingCart(
      UUID shoppingCartId,
      PricedProductItem productItem
    ) implements ShoppingCartCommand {
    }

    record ConfirmShoppingCart(
      UUID shoppingCartId
    ) implements ShoppingCartCommand {
    }

    record CancelShoppingCart(
      UUID shoppingCartId
    ) implements ShoppingCartCommand {
    }
  }

  public static ShoppingCartOpened handle(OpenShoppingCart command) {
    return new ShoppingCartOpened(
      command.shoppingCartId(),
      command.clientId()
    );
  }

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

  public static ProductItemRemovedFromShoppingCart handle(
    RemoveProductItemFromShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Adding product item for cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    shoppingCart.productItems().hasEnough(command.productItem());

    return new ProductItemRemovedFromShoppingCart(
      command.shoppingCartId(),
      command.productItem()
    );
  }

  public static ShoppingCartConfirmed handle(ConfirmShoppingCart command, ShoppingCart shoppingCart) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    return new ShoppingCartConfirmed(
      shoppingCart.id(),
      OffsetDateTime.now()
    );
  }

  public static ShoppingCartCanceled handle(CancelShoppingCart command, ShoppingCart shoppingCart) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    return new ShoppingCartCanceled(
      shoppingCart.id(),
      OffsetDateTime.now()
    );
  }
}
