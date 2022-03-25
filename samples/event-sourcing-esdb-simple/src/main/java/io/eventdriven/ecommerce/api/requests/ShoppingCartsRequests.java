package io.eventdriven.ecommerce.api.requests;

import java.util.UUID;

public final class ShoppingCartsRequests {
  public record Open(
    UUID clientId
  ) {
  }

  public record ProductItemRequest(
    UUID productId,
    Integer quantity
  ) {
  }

  public record AddProduct(
    ProductItemRequest productItem
  ) {
  }
}
