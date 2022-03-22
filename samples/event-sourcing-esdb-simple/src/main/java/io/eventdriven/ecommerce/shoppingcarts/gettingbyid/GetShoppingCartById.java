package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import java.util.Optional;
import java.util.UUID;

public record GetShoppingCartById(
  UUID shoppingCartId
) {
  public static GetShoppingCartById of(UUID cartId)
  {
    if (cartId == null)
      throw new IllegalArgumentException("Cart id has to be defined");

    return new GetShoppingCartById(cartId);
  }

  public static Optional<ShoppingCartDetails> handle(
    ShoppingCartDetailsRepository repository,
    GetShoppingCartById query
  )
  {
    return repository.findById(query.shoppingCartId());
  }
}
