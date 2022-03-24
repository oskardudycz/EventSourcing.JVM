package io.eventdriven.ecommerce.shoppingcarts.canceling;

import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;

import java.time.LocalDateTime;
import java.util.UUID;

public record CancelShoppingCart(
  UUID shoppingCartId,
  Long expectedVersion
) {
  public static Events.ShoppingCartCanceled handle(CancelShoppingCart command, ShoppingCart shoppingCart) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    return new Events.ShoppingCartCanceled(
      shoppingCart.id(),
      LocalDateTime.now()
    );
  }
}
