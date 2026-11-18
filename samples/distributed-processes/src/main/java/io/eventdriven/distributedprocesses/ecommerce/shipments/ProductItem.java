package io.eventdriven.distributedprocesses.ecommerce.shipments;

import java.util.UUID;

public record ProductItem(UUID productId, int quantity) {}
