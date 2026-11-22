package io.eventdriven.ecommerce.shoppingcarts;

import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.ecommerce.shoppingcarts.productitems.PricedProductItem;

public final class ShoppingCartDecider {
    private ShoppingCartDecider() {}

    public static ShoppingCartEvent[] decide(ShoppingCartCommand command, ShoppingCart state) {
        return switch (command) {
            case AddProductItemToShoppingCart add -> addProductItem(add, state);
            case RemoveProductItemFromShoppingCart remove -> removeProductItem(remove, state);
            case CheckOutShoppingCart checkOut -> checkOut(checkOut, state);
        };
    }

    public static ShoppingCartEvent[] addProductItem(AddProductItemToShoppingCart command, ShoppingCart state) {
        if (state.status() != null && state.status().isClosed())
            throw new IllegalStateException(
                    "Adding product item for cart in '%s' status is not allowed.".formatted(state.status()));

        var productItem = command.productItem();
        var unitPrice = state.productItems()
                .find(productItem.productId())
                .map(PricedProductItem::unitPrice)
                .orElseGet(command::currentUnitPrice);

        return new ShoppingCartEvent[] {
            new ProductItemAddedToShoppingCart(
                    command.shoppingCartId(),
                    command.customerId(),
                    new PricedProductItem(productItem, unitPrice),
                    command.now())
        };
    }

    public static ShoppingCartEvent[] removeProductItem(RemoveProductItemFromShoppingCart command, ShoppingCart state) {
        if (state.isClosed())
            throw new IllegalStateException(
                    "Removing product item for cart in '%s' status is not allowed.".formatted(state.status()));

        var pricedProductItem = state.productItems()
                .find(command.productItem().productId())
                .map(current -> new PricedProductItem(command.productItem(), current.unitPrice()))
                .orElseThrow(() -> new IllegalStateException("Product item wasn't found"));

        state.productItems().assertThatCanRemove(pricedProductItem);

        return new ShoppingCartEvent[] {
            new ProductItemRemovedFromShoppingCart(state.id(), pricedProductItem, command.now())
        };
    }

    public static ShoppingCartEvent[] checkOut(CheckOutShoppingCart command, ShoppingCart state) {
        if (state.isCheckedOut()) return new ShoppingCartEvent[0];

        if (state.isClosed())
            throw new IllegalStateException(
                    "Checking out cart in '%s' status is not allowed.".formatted(state.status()));

        if (state.productItems().items().isEmpty())
            throw new IllegalStateException("Checking out an empty cart is not allowed.");

        var priceChanges = state.productItems().items().stream()
                .map(current -> new ProductItemPriceChanged(
                        state.id(),
                        current.productId(),
                        current.unitPrice(),
                        command.currentUnitPrices().get(current.productId()),
                        command.now()))
                .filter(change -> change.newUnitPrice() != change.oldUnitPrice())
                .toArray(ShoppingCartEvent[]::new);

        if (priceChanges.length > 0) return priceChanges;

        return new ShoppingCartEvent[] {new ShoppingCartCheckedOut(state.id(), command.now())};
    }
}
