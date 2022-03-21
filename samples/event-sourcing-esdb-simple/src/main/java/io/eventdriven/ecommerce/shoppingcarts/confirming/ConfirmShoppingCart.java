package io.eventdriven.ecommerce.shoppingcarts.confirming;

import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConfirmShoppingCart(
  UUID shoppingCartId,
  Long expectedVersion
) {
  public static ConfirmShoppingCart From(UUID cartId, Long expectedVersion) {
    if (cartId == null)
      throw new IllegalArgumentException("Cart id has to be defined");

    if (expectedVersion == null)
      throw new IllegalArgumentException("Expected version has to be provided");

    return new ConfirmShoppingCart(cartId, expectedVersion);
  }

  public static Events.ShoppingCartConfirmed Handle(ConfirmShoppingCart command, ShoppingCart shoppingCart) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Confirming cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    return new Events.ShoppingCartConfirmed(
      shoppingCart.id(),
      LocalDateTime.now()
    );
  }
}
