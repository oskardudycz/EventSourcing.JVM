package io.eventdriven.ecommerce.shoppingcarts.initializing;

import io.eventdriven.ecommerce.shoppingcarts.Events;

import java.util.UUID;

public record InitializeShoppingCart(
  UUID shoppingCartId,
  UUID clientId
)
{
  public static InitializeShoppingCart From(UUID cartId, UUID clientId)
  {
    if (cartId == null)
      throw new IllegalArgumentException("Cart id has to be defined");
    if (clientId == null)
      throw new IllegalArgumentException("Client id has to be defined");

    return new InitializeShoppingCart(cartId, clientId);
  }

  public static Events.ShoppingCartInitialized Handle(InitializeShoppingCart command)
  {
    return new Events.ShoppingCartInitialized(
      command.shoppingCartId(),
      command.clientId()
    );
  }
}
