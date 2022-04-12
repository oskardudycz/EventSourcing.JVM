package io.eventdriven.ecommerce.shoppingcarts.confirming;

import java.util.UUID;

public record ConfirmShoppingCart(
  UUID shoppingCartId,
  Long expectedVersion
) {
}
