package io.eventdriven.introductiontoeventsourcing.e07_application_logic.esdb.immutable.app.shoppingcarts.productitems;

import java.util.UUID;

public record ProductItem(
  UUID productId,
  int quantity
) {
  public ProductItem {
    if (quantity <= 0)
      throw new IllegalArgumentException("Quantity has to be a positive number");
  }
}
