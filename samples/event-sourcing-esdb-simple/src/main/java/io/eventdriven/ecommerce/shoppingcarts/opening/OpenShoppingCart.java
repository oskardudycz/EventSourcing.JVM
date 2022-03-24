package io.eventdriven.ecommerce.shoppingcarts.opening;

import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartOpened;

import java.util.UUID;

public record OpenShoppingCart(
  UUID shoppingCartId,
  UUID clientId
) {
  public static ShoppingCartOpened handle(OpenShoppingCart command) {
    return new ShoppingCartOpened(
      command.shoppingCartId(),
      command.clientId()
    );
  }
}
