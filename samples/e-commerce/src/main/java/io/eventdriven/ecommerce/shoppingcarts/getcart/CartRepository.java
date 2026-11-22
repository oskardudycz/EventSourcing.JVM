package io.eventdriven.ecommerce.shoppingcarts.getcart;

import io.eventdriven.core.projections.ProjectionRepository;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;

public interface CartRepository extends ProjectionRepository<Cart, ShoppingCart.Id> {}
