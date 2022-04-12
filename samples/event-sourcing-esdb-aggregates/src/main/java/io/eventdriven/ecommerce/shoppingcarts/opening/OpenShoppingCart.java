package io.eventdriven.ecommerce.shoppingcarts.opening;

import java.util.UUID;

public record OpenShoppingCart(
  UUID shoppingCartId,
  UUID clientId
) {
}
