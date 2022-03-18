package io.eventdriven.ecommerce.shoppingcarts.canceling;

import io.eventdriven.ecommerce.shoppingcarts.Events;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;

import java.time.LocalDateTime;
import java.util.UUID;

public record CancelShoppingCart(
  UUID shoppingCartId
)
{
  public static CancelShoppingCart From(UUID cartId)
  {
    if (cartId == null)
      throw new IllegalArgumentException("Cart id has to be defined");

    return new CancelShoppingCart(cartId);
  }

  public static Events.ShoppingCartCanceled Handle(CancelShoppingCart command, ShoppingCart shoppingCart)
  {
    if(shoppingCart.isClosed())
      throw new IllegalStateException("Canceling cart in '%s' status is not allowed.".formatted(shoppingCart.status()));

    return new Events.ShoppingCartCanceled(
      shoppingCart.id(),
      LocalDateTime.now()
    );
  }
}
