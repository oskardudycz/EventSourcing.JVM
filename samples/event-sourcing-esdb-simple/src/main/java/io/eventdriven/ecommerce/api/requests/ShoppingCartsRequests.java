package io.eventdriven.ecommerce.api.requests;

import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.UUID;

public final class ShoppingCartsRequests {
  public record Open(
    @NotNull UUID clientId
  ) {
  }

  @Validated
  public record ProductItemRequest(
    @NotNull UUID productId,
    @NotNull Integer quantity
  ) {
  }

  public record AddProduct(
    @NotNull ProductItemRequest productItem
  ) {
  }
}
