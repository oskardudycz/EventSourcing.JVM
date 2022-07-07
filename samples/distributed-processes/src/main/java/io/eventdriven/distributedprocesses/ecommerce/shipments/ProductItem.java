package io.eventdriven.distributedprocesses.ecommerce.shipments;

import java.util.UUID;

public record ProductItem(
  UUID id,
  UUID productId,
  int quantity
) {
}
