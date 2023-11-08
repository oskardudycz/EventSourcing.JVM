package io.eventdriven.ecommerce.shoppingcarts;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;

import java.util.UUID;

public sealed interface ShoppingCartCommand {
  record Open(
    UUID clientId
  ) implements ShoppingCartCommand {
  }

  record AddProductItem(
    PricedProductItem productItem
  ) implements ShoppingCartCommand {
  }

  record RemoveProductItem(
    PricedProductItem productItem
  ) implements ShoppingCartCommand {
  }

  record Confirm() implements ShoppingCartCommand {
  }

  record Cancel() implements ShoppingCartCommand {
  }
}
