package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.ProductItems;

public record ShoppingCart(ShoppingCartId id, ProductItems productItems, ShoppingCartStatus status) {
    public static ShoppingCart initial() {
        return new ShoppingCart(null, ProductItems.empty(), null);
    }

    public boolean isCheckedOut() {
        return status == ShoppingCartStatus.CheckedOut;
    }

    boolean isClosed() {
        return status == null || status.isClosed();
    }

    public static ShoppingCart evolve(ShoppingCart state, ShoppingCartEvent event) {
        return switch (event) {
            case ProductItemAddedToShoppingCart added ->
                new ShoppingCart(
                        state.status == null ? added.shoppingCartId() : state.id,
                        state.productItems.add(added.productItem()),
                        state.status == null ? ShoppingCartStatus.Pending : state.status);
            case ProductItemRemovedFromShoppingCart removed ->
                new ShoppingCart(state.id, state.productItems.remove(removed.productItem()), state.status);
            case ProductItemPriceChanged priceChanged ->
                new ShoppingCart(
                        state.id,
                        state.productItems.changeUnitPrice(priceChanged.productId(), priceChanged.newUnitPrice()),
                        state.status);
            case ShoppingCartCheckedOut ignored ->
                new ShoppingCart(state.id, state.productItems, ShoppingCartStatus.CheckedOut);
        };
    }
}
