package io.eventdriven.distributedprocesses.shoppingcarts.productitems;

import java.util.UUID;

public record ProductItem(
  UUID productId,
  int quantity
) {}
