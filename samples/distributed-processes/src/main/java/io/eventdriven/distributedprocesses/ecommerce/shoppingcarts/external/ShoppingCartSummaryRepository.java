package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import io.eventdriven.distributedprocesses.core.projections.ProjectionRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

public interface ShoppingCartSummaryRepository extends ProjectionRepository<ShoppingCartSummary, ShoppingCartId> {}
