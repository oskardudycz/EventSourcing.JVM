package io.eventdriven.ecommerce.shoppingcarts.external;

import io.eventdriven.core.projections.ProjectionRepository;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;

public interface ShoppingCartSummaryRepository extends ProjectionRepository<ShoppingCartSummary, ShoppingCart.Id> {}
