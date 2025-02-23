package io.eventdriven.introductiontoeventsourcing.e08_optimistic_concurrency.postgresql.mixed.app.api;

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
