package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.api;

import io.eventdriven.distributedprocesses.core.projections.ProjectionRepository;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;

public interface CartRepository extends ProjectionRepository<Cart, ShoppingCartId> {}
