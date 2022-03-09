package io.eventdriven.ecommerce.api.requests;

import java.util.UUID;

public final class ShoppingCartsRequests {
  public record InitializeShoppingCartRequest(
    UUID clientId
  ) {
  }

  public record ProductItemRequest(
    UUID ProductId,
    Integer quantity
  ) {
  }

  public record AddProductRequest(
    ProductItemRequest productItem
  ) {
  }

  public record PricedProductItemRequest(
    UUID ProductId,
    Integer Quantity,
    Double UnitPrice
  ) {
  }

  public record RemoveProductRequest(
    PricedProductItemRequest ProductItem
  ) {
  }

  public class ConfirmShoppingCartRequest {
  }
}
