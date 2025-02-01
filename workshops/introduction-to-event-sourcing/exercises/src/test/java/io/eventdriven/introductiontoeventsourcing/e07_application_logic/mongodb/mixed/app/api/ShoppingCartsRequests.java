package io.eventdriven.introductiontoeventsourcing.e07_application_logic.mongodb.mixed.app.api;

import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;

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
