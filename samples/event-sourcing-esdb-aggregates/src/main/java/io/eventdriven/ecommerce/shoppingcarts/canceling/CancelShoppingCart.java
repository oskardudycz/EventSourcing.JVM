package io.eventdriven.ecommerce.shoppingcarts.canceling;

import java.util.UUID;

public record CancelShoppingCart(
  UUID shoppingCartId,
  Long expectedVersion
) {
}
