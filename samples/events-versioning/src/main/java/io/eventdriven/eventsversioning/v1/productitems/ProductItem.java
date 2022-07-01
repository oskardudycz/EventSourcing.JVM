package io.eventdriven.eventsversioning.v1.productitems;

import java.util.UUID;

public record ProductItem(
  UUID productId,
  int quantity
) {}
