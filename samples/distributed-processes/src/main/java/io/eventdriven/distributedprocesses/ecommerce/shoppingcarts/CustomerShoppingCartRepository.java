package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import io.eventdriven.distributedprocesses.core.projections.ProjectionRepository;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CustomerShoppingCartRepository extends ProjectionRepository<CustomerShoppingCart, ShoppingCartId> {
    Optional<CustomerShoppingCart> findFirstByCustomerIdAndStatusNotIn(
            UUID customerId, Collection<ShoppingCartStatus> statuses);

    int countByCustomerId(UUID customerId);
}
