package io.eventdriven.ecommerce.shoppingcarts.currentcart;

import io.eventdriven.core.projections.ProjectionRepository;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CustomerShoppingCartRepository extends ProjectionRepository<CustomerShoppingCart, ShoppingCart.Id> {
    Optional<CustomerShoppingCart> findFirstByCustomerIdAndStatusNotIn(
            UUID customerId, Collection<ShoppingCart.Status> statuses);

    int countByCustomerId(UUID customerId);
}
