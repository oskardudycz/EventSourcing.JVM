package io.eventdriven.ecommerce.shoppingcarts.confirming;

import io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.ShoppingCartConfirmed;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConfirmShoppingCart(
  UUID shoppingCartId,
  Long expectedVersion
) {
  public static ShoppingCartConfirmed handle(ConfirmShoppingCart command, ShoppingCart shoppingCart) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    return new ShoppingCartConfirmed(
      shoppingCart.id(),
      LocalDateTime.now()
    );
  }
}
