package io.eventdriven.ecommerce.shoppingcarts.canceling;

import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;

import java.time.LocalDateTime;
import java.util.UUID;

public record CancelShoppingCart(
  UUID shoppingCartId,
  Long expectedVersion
) {
  public static CancelShoppingCart from(UUID cartId, Long expectedVersion) {
    if (cartId == null)
      throw new IllegalArgumentException("Cart id has to be defined");

    if (expectedVersion == null)
      throw new IllegalArgumentException("Expected version has to be provided");

    return new CancelShoppingCart(cartId, expectedVersion);
  }

  public static Events.ShoppingCartCanceled Handle(CancelShoppingCart command, ShoppingCart shoppingCart) {
    if (shoppingCart.isClosed())
      throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    return new Events.ShoppingCartCanceled(
      shoppingCart.id(),
      LocalDateTime.now()
    );
  }
}
