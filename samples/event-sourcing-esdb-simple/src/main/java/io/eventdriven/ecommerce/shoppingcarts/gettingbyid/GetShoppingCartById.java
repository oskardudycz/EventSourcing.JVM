package io.eventdriven.ecommerce.shoppingcarts.gettingbyid;

import java.util.Optional;
import java.util.UUID;

public record GetShoppingCartById(
  UUID shoppingCartId
) {
  public static Optional<ShoppingCartDetails> handle(
    ShoppingCartDetailsRepository repository,
    GetShoppingCartById query
  )
  {
    return repository.findById(query.shoppingCartId());
  }
}
