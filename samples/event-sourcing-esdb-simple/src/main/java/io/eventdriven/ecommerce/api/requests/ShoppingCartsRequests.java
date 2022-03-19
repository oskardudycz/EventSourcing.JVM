package io.eventdriven.ecommerce.api.requests;

import java.util.UUID;

public final class ShoppingCartsRequests {
  public record InitializeShoppingCartRequest(
    UUID clientId
  ) {
  }

  public record ProductItemRequest(
    UUID productId,
    Integer quantity
  ) {
  }

  public record AddProductRequest(
    ProductItemRequest productItem
  ) {
  }
}
