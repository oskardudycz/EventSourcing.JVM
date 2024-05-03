package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts;

import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts.productitems.ProductItem;

import java.util.UUID;

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

  record ConfirmShoppingCart(
    UUID shoppingCartId
  ) implements ShoppingCartCommand {
  }

  record RemoveProductItemFromShoppingCart(
    UUID shoppingCartId,
    PricedProductItem productItem
  ) implements ShoppingCartCommand {
  }

  record CancelShoppingCart(
    UUID shoppingCartId
  ) implements ShoppingCartCommand {
  }
}
