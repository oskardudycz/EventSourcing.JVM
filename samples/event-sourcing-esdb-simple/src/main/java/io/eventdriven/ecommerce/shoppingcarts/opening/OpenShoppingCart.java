package io.eventdriven.ecommerce.shoppingcarts.opening;

import io.eventdriven.ecommerce.shoppingcarts.Events;

import java.util.UUID;

public record OpenShoppingCart(
  UUID shoppingCartId,
  UUID clientId
) {
  public static Events.ShoppingCartOpened handle(OpenShoppingCart command) {
    return new Events.ShoppingCartOpened(
      command.shoppingCartId(),
      command.clientId()
    );
  }
}
