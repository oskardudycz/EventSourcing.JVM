package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.distributedprocesses.core.projections.Projection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public class CustomerShoppingCarts extends Projection<CustomerShoppingCart, ShoppingCartId> {
    private final CustomerShoppingCartRepository repository;

    public CustomerShoppingCarts(CustomerShoppingCartRepository repository) {
        super(repository);
        this.repository = repository;
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(ProductItemAddedToShoppingCart.class, ShoppingCartCheckedOut.class);
    }

    @Override
    protected ShoppingCartId getDocumentId(Object event) {
        return switch (event) {
            case ProductItemAddedToShoppingCart e -> e.shoppingCartId();
            case ShoppingCartCheckedOut e -> e.shoppingCartId();
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
        };
    }

    @Override
    protected @Nullable CustomerShoppingCart evolve(@Nullable CustomerShoppingCart current, Object event) {
        return switch (event) {
            case ProductItemAddedToShoppingCart e ->
                current != null
                        ? current
                        : new CustomerShoppingCart(e.customerId(), e.shoppingCartId(), ShoppingCartStatus.Pending);
            case ShoppingCartCheckedOut _ ->
                current != null
                        ? new CustomerShoppingCart(
                                current.getCustomerId(), current.getShoppingCartId(), ShoppingCartStatus.CheckedOut)
                        : null;
            default -> current;
        };
    }

    public Optional<ShoppingCartId> currentOf(UUID customerId) {
        return repository
                .findFirstByCustomerIdAndStatusNotIn(customerId, ShoppingCartStatus.Closed)
                .map(CustomerShoppingCart::getShoppingCartId);
    }

    public int countOf(UUID customerId) {
        return repository.countByCustomerId(customerId);
    }
}
