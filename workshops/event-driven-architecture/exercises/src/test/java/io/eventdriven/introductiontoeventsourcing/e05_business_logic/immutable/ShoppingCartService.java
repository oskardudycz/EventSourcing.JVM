package io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable;

import io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ProductItems.ProductPriceCalculator;

import java.util.UUID;
import static io.eventdriven.introductiontoeventsourcing.e05_business_logic.immutable.ProductItems.ProductItems.*;
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
    throw new RuntimeException("Fill the implementation part");
  }

  public static ProductItemAddedToShoppingCart handle(
    ProductPriceCalculator productPriceCalculator,
    AddProductItemToShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    throw new RuntimeException("Fill the implementation part");
  }

  public static ProductItemRemovedFromShoppingCart handle(
    RemoveProductItemFromShoppingCart command,
    ShoppingCart shoppingCart
  ) {
    throw new RuntimeException("Fill the implementation part");
  }

  public static ShoppingCartConfirmed handle(ConfirmShoppingCart command, ShoppingCart shoppingCart) {
    throw new RuntimeException("Fill the implementation part");
  }

  public static ShoppingCartCanceled handle(CancelShoppingCart command, ShoppingCart shoppingCart) {
    throw new RuntimeException("Fill the implementation part");
  }
}
