package io.eventdriven.ecommerce.shoppingcarts.currentcart;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.core.projections.Projection;
import io.eventdriven.ecommerce.shoppingcarts.ShoppingCart;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public class CustomerShoppingCarts extends Projection<CustomerShoppingCart, ShoppingCart.Id> {
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
    protected ShoppingCart.Id getDocumentId(Object event) {
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
                        : new CustomerShoppingCart(e.customerId(), e.shoppingCartId(), ShoppingCart.Status.Pending);
            case ShoppingCartCheckedOut _ ->
                current != null
                        ? new CustomerShoppingCart(
                                current.getCustomerId(), current.getShoppingCartId(), ShoppingCart.Status.CheckedOut)
                        : null;
            default -> current;
        };
    }

    public Optional<ShoppingCart.Id> currentOf(UUID customerId) {
        return repository
                .findFirstByCustomerIdAndStatusNotIn(customerId, ShoppingCart.Status.Closed)
                .map(CustomerShoppingCart::getShoppingCartId);
    }

    public int countOf(UUID customerId) {
        return repository.countByCustomerId(customerId);
    }
}
