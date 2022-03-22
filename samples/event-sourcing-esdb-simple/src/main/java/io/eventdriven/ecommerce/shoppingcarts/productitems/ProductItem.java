package io.eventdriven.ecommerce.shoppingcarts.productitems;

import java.util.UUID;

public record ProductItem(
  UUID productId,
  int quantity
) {
  public static ProductItem of(UUID productId, Integer quantity) {
    if (productId == null)
      throw new IllegalArgumentException("ProductId has to be defined");

    if (quantity == null || quantity <= 0)
      throw new IllegalArgumentException("Quantity has to be a positive number");

    return new ProductItem(productId, quantity);
  }
}
