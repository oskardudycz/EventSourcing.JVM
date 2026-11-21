package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.external;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.distributedprocesses.core.projections.Projection;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartId;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public class ShoppingCartSummaries extends Projection<ShoppingCartSummary, ShoppingCartId> {
    public ShoppingCartSummaries(ShoppingCartSummaryRepository repository) {
        super(repository);
    }

    @Override
    public List<Class<?>> canHandle() {
        return List.of(
                ProductItemAddedToShoppingCart.class,
                ProductItemRemovedFromShoppingCart.class,
                ProductItemPriceChanged.class,
                ShoppingCartCheckedOut.class);
    }

    @Override
    protected ShoppingCartId getDocumentId(Object event) {
        return switch (event) {
            case ProductItemAddedToShoppingCart e -> e.shoppingCartId();
            case ProductItemRemovedFromShoppingCart e -> e.shoppingCartId();
            case ProductItemPriceChanged e -> e.shoppingCartId();
            case ShoppingCartCheckedOut e -> e.shoppingCartId();
            default -> throw new IllegalArgumentException("Unexpected event %s".formatted(event));
        };
    }

    @Override
    protected @Nullable ShoppingCartSummary evolve(@Nullable ShoppingCartSummary current, Object event) {
        return switch (event) {
            case ProductItemAddedToShoppingCart e ->
                current == null
                        ? withProductItems(
                                e.shoppingCartId(),
                                e.customerId(),
                                ProductItems.empty().add(e.productItem()))
                        : withProductItems(
                                current.getShoppingCartId(),
                                current.getCustomerId(),
                                productItemsOf(current).add(e.productItem()));
            case ProductItemRemovedFromShoppingCart e ->
                withProductItems(
                        current.getShoppingCartId(),
                        current.getCustomerId(),
                        productItemsOf(current).remove(e.productItem()));
            case ProductItemPriceChanged e ->
                withProductItems(
                        current.getShoppingCartId(),
                        current.getCustomerId(),
                        productItemsOf(current).changeUnitPrice(e.productId(), e.newUnitPrice()));
            default -> current;
        };
    }

    private static ShoppingCartSummary withProductItems(
            ShoppingCartId shoppingCartId, UUID customerId, ProductItems productItems) {
        return new ShoppingCartSummary(
                shoppingCartId,
                customerId,
                productItems.items().toArray(new PricedProductItem[0]),
                productItems.totalPrice());
    }

    private static ProductItems productItemsOf(ShoppingCartSummary summary) {
        return new ProductItems(new ArrayList<>(Arrays.asList(summary.getProductItems())));
    }
}
