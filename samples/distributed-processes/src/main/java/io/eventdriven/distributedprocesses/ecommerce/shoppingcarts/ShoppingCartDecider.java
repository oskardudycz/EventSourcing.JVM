package io.eventdriven.distributedprocesses.ecommerce.shoppingcarts;

import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartCommand.*;
import static io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.ShoppingCartEvent.*;

import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.pricing.ProductPriceCalculator;
import io.eventdriven.distributedprocesses.ecommerce.shoppingcarts.productitems.PricedProductItem;
import java.time.OffsetDateTime;
import java.util.function.Supplier;

public class ShoppingCartDecider {
    private final ProductPriceCalculator productPriceCalculator;
    private final Supplier<OffsetDateTime> now;

    public ShoppingCartDecider(ProductPriceCalculator productPriceCalculator, Supplier<OffsetDateTime> now) {
        this.productPriceCalculator = productPriceCalculator;
        this.now = now;
    }

    public ShoppingCartEvent[] handle(ShoppingCartCommand command, ShoppingCart state) {
        return switch (command) {
            case AddProductItemToShoppingCart add -> addProductItem(add, state);
            case RemoveProductItemFromShoppingCart remove -> removeProductItem(remove, state);
            case CheckOutShoppingCart checkOut -> checkOut(state);
        };
    }

    private ShoppingCartEvent[] addProductItem(AddProductItemToShoppingCart command, ShoppingCart state) {
        if (state.status() != null && state.status().isClosed())
            throw new IllegalStateException(
                    "Adding product item for cart in '%s' status is not allowed.".formatted(state.status()));

        var productItem = command.productItem();
        var unitPrice = state.productItems()
                .find(productItem.productId())
                .map(PricedProductItem::unitPrice)
                .orElseGet(() -> productPriceCalculator.calculate(productItem).unitPrice());

        return new ShoppingCartEvent[] {
            new ProductItemAddedToShoppingCart(
                    command.shoppingCartId(),
                    command.customerId(),
                    new PricedProductItem(productItem, unitPrice),
                    now.get())
        };
    }

    private ShoppingCartEvent[] removeProductItem(RemoveProductItemFromShoppingCart command, ShoppingCart state) {
        if (state.isClosed())
            throw new IllegalStateException(
                    "Removing product item for cart in '%s' status is not allowed.".formatted(state.status()));

        var pricedProductItem = state.productItems()
                .find(command.productItem().productId())
                .map(current -> new PricedProductItem(command.productItem(), current.unitPrice()))
                .orElseThrow(() -> new IllegalStateException("Product item wasn't found"));

        state.productItems().assertThatCanRemove(pricedProductItem);

        return new ShoppingCartEvent[] {new ProductItemRemovedFromShoppingCart(state.id(), pricedProductItem, now.get())
        };
    }

    private ShoppingCartEvent[] checkOut(ShoppingCart state) {
        if (state.isCheckedOut()) return new ShoppingCartEvent[0];

        if (state.isClosed())
            throw new IllegalStateException(
                    "Checking out cart in '%s' status is not allowed.".formatted(state.status()));

        if (state.productItems().items().isEmpty())
            throw new IllegalStateException("Checking out an empty cart is not allowed.");

        var changedAt = now.get();
        var priceChanges = state.productItems().items().stream()
                .map(current -> new ProductItemPriceChanged(
                        state.id(),
                        current.productId(),
                        current.unitPrice(),
                        productPriceCalculator.calculate(current.productItem()).unitPrice(),
                        changedAt))
                .filter(change -> change.newUnitPrice() != change.oldUnitPrice())
                .toArray(ShoppingCartEvent[]::new);

        if (priceChanges.length > 0) return priceChanges;

        return new ShoppingCartEvent[] {new ShoppingCartCheckedOut(state.id(), changedAt)};
    }
}
