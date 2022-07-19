package io.eventdriven.uniqueness.shoppingcarts;

import java.util.UUID;

public record ShoppingCartStreamId(UUID clientId) {
  @Override
  public String toString() {
    return "shopping_cart-%s".formatted(
      clientId().toString().replace("-", "")
    );
  }
}
