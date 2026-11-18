package io.eventdriven.distributedprocesses.ecommerce.orders.products;

import java.util.UUID;

public record PricedProductItem(UUID productId, int quantity, double unitPrice) {}
