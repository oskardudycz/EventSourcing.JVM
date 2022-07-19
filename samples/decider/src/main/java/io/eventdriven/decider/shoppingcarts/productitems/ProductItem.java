package io.eventdriven.decider.shoppingcarts.productitems;

import java.util.UUID;

public record ProductItem(
  UUID productId,
  int quantity
) {}
