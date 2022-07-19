package io.eventdriven.decider.shoppingcarts.productitems;

import java.util.UUID;

public record PricedProductItem(
  UUID productId,
  int quantity,
  double unitPrice
) {}
