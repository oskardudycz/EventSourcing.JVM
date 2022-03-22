package io.eventdriven.ecommerce.shoppingcarts.opening;

import io.eventdriven.ecommerce.shoppingcarts.Events;

import java.util.UUID;

public record OpenShoppingCart(
  UUID shoppingCartId,
  UUID clientId
)
{
  public static OpenShoppingCart of(UUID cartId, UUID clientId)
  {
    if (cartId == null)
      throw new IllegalArgumentException("Cart id has to be defined");
    if (clientId == null)
      throw new IllegalArgumentException("Client id has to be defined");

    return new OpenShoppingCart(cartId, clientId);
  }

  public static Events.ShoppingCartOpened handle(OpenShoppingCart command)
  {
    return new Events.ShoppingCartOpened(
      command.shoppingCartId(),
      command.clientId()
    );
  }
}
